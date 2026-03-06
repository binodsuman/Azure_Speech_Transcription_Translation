package com.example.speechapp.service;

import com.example.speechapp.config.AzureSpeechConfig;
import com.example.speechapp.config.TranslatorConfig;
import com.example.speechapp.model.TranscriptionJob;
import com.example.speechapp.model.TranscriptionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class SpeechService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechService.class);

    @Autowired
    private AzureSpeechConfig speechConfig;

    @Autowired
    private TranslatorConfig translatorConfig;

    @Autowired
    private BlobStorageService blobStorageService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private EmailService emailService;

    @Value("${job.polling.max-attempts:120}")
    private int maxPollingAttempts;

    @Value("${job.status.update-interval:5}")
    private int statusUpdateInterval;

    @Value("${transcription.keep-original-language:true}")
    private boolean keepOriginalLanguage;

    @Value("${transcription.target-language:en}")
    private String targetLanguage;

    @Value("${transcription.auto-detect:true}")
    private boolean autoDetectLanguage;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, TranscriptionJob> jobStore = new ConcurrentHashMap<>();

    public TranscriptionJob startTranscriptionJob(MultipartFile file, String email) throws IOException {
        String originalFilename = file.getOriginalFilename();

        logger.info("=".repeat(80));
        logger.info("PROCESSING FILE: {}", originalFilename);
        logger.info("Language settings - Auto-detect: {}, Target: {}",
                autoDetectLanguage, targetLanguage);
        logger.info("=".repeat(80));

        // Check if file already exists and has transcript
        if (blobStorageService.fileExists(originalFilename)) {
            String baseName = originalFilename.replace(".wav", "");
            String possibleTranscripts = checkExistingTranscripts(baseName);

            if (possibleTranscripts != null) {
                logger.info("Found existing transcript: {}", possibleTranscripts);
                String transcriptJson = blobStorageService.getTranscriptContent(possibleTranscripts);

                if (transcriptJson != null) {
                    TranscriptionResult result = objectMapper.readValue(transcriptJson, TranscriptionResult.class);

                    // Create completed job with existing data
                    TranscriptionJob job = new TranscriptionJob();
                    job.setJobId("EXISTING-" + UUID.randomUUID().toString());
                    job.setFileName(originalFilename);
                    job.setFileSize(file.getSize());
                    job.setEmail(email);
                    job.setStatus(TranscriptionJob.JobStatus.COMPLETED);
                    job.setProgress(100);
                    job.setMessage("Using existing transcript from blob storage");
                    job.setCreatedAt(LocalDateTime.now());
                    job.setCompletedAt(LocalDateTime.now());

                    // Parse segments with both original and translated text
                    List<SpeakerSegment> segments = parseTranscriptionResult(result);
                    job.setSegments(segments);
                    job.setDetectedLanguage(result.getDetectedLanguage());
                    job.setTranslated(result.isTranslated());

                    // Check if this is English or non-English
                    boolean isEnglish = translationService.isEnglish(result.getDetectedLanguage());
                    job.setEnglishAudio(isEnglish);

                    logger.info("✓ Retrieved existing transcript with {} segments", segments.size());
                    logger.info("   Detected Language: {}", result.getDetectedLanguage());
                    logger.info("   Is English: {}", isEnglish);
                    logger.info("=".repeat(80));

                    jobStore.put(job.getJobId(), job);
                    return job;
                }
            }
        }

        // Start new transcription job
        logger.info("No existing transcript found. Starting new transcription job...");

        TranscriptionJob job = new TranscriptionJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setFileName(originalFilename);
        job.setFileSize(file.getSize());
        job.setEmail(email);
        job.setStatus(TranscriptionJob.JobStatus.UPLOADING);
        job.setMessage("Starting transcription job...");
        job.setProgress(0);
        job.setKeepOriginalLanguage(keepOriginalLanguage);
        job.setTranslationTargetLanguage(targetLanguage);

        jobStore.put(job.getJobId(), job);

        logger.info("Job ID assigned: {}", job.getJobId());

        // Start async processing
        processJobAsync(job, file);

        return job;
    }

    private String checkExistingTranscripts(String baseName) {
        String[] possibleSuffixes = {"", "_en", "_es", "_fr", "_de", "_it", "_pt", "_ru", "_zh", "_ja", "_ko", "_hi"};

        for (String suffix : possibleSuffixes) {
            String transcriptName = baseName + suffix + "_transcript.json";
            if (blobStorageService.transcriptExists(transcriptName)) {
                return transcriptName;
            }
        }
        return null;
    }

    @Async
    protected void processJobAsync(TranscriptionJob job, MultipartFile file) {
        String sasUrl = null;
        String originalFilename = file.getOriginalFilename();

        try {
            // Step 1: Upload to Blob Storage
            updateJobProgress(job, TranscriptionJob.JobStatus.UPLOADING, 10,
                    "Uploading to Azure Blob Storage...");

            logger.info("Step 1/6: Uploading file to Blob Storage");
            sasUrl = blobStorageService.uploadFileAndGenerateSas(file, originalFilename);
            job.setBlobSasUrl(sasUrl);
            logger.info("✓ File uploaded successfully");

            // Step 2: Create Transcription Job
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 20,
                    "Creating Azure Speech transcription job...");

            logger.info("Step 2/6: Creating Azure Speech transcription job");

            String transcriptionLanguage = autoDetectLanguage ? null : speechConfig.getLanguage();
            if ("auto".equals(speechConfig.getLanguage())) {
                transcriptionLanguage = null;
            }

            String transcriptionUrl = createTranscription(sasUrl, job.getJobId(), transcriptionLanguage);
            job.setAzureTranscriptionUrl(transcriptionUrl);
            logger.info("✓ Transcription job created");

            // Step 3: Poll for Completion
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 30,
                    "Processing audio with speaker diarization...");

            logger.info("Step 3/6: Polling for transcription completion");
            TranscriptionResult result = pollForTranscriptionResult(transcriptionUrl, job);

            // Detect language from transcription
            if (autoDetectLanguage && result.getRecognizedPhrases() != null && !result.getRecognizedPhrases().isEmpty()) {
                String sampleText = result.getRecognizedPhrases().get(0).getNBest().get(0).getDisplay();
                String detectedLanguage = translationService.detectLanguage(sampleText);
                result.setDetectedLanguage(detectedLanguage);
                job.setDetectedLanguage(detectedLanguage);
                logger.info("Auto-detected language: {}", detectedLanguage);
            }

            // Check if audio is English
            boolean isEnglish = translationService.isEnglish(result.getDetectedLanguage());
            job.setEnglishAudio(isEnglish);

            // Step 4: Parse Results
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 60,
                    "Parsing transcription results...");

            logger.info("Step 4/6: Parsing transcription results");
            List<SpeakerSegment> segments = parseTranscriptionResult(result);
            job.setSegments(segments);
            logger.info("✓ Parsed {} speaker segments", segments.size());

            // Step 5: Translate if needed (only for non-English audio)
            boolean needsTranslation = !isEnglish && !targetLanguage.equals(result.getDetectedLanguage());

            if (needsTranslation) {
                updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 75,
                        "Translating non-English content to " + targetLanguage + "...");

                logger.info("Step 5/6: Translating from {} to {}",
                        result.getDetectedLanguage(), targetLanguage);

                // Translate each segment and store both versions
                for (SpeakerSegment segment : segments) {
                    String translatedText = translationService.translateText(
                            segment.getText(), result.getDetectedLanguage());
                    segment.setTranslatedText(translatedText);
                }

                result.setTranslated(true);
                job.setTranslated(true);
                job.setOriginalLanguage(result.getDetectedLanguage());

                logger.info("✓ Translation completed");
            } else {
                logger.info("No translation needed (audio is in English)");
                // For English audio, just set translated text same as original
                for (SpeakerSegment segment : segments) {
                    segment.setTranslatedText(segment.getText());
                }
            }

            // Step 6: Save Transcripts to Blob
            updateJobProgress(job, TranscriptionJob.JobStatus.PROCESSING, 90,
                    "Saving transcripts to blob storage...");

            logger.info("Step 6/6: Saving transcripts to blob storage");

            // Save transcripts with language suffix
            String baseName = originalFilename.replace(".wav", "");
            String languageSuffix = result.getDetectedLanguage() != null ?
                    "_" + translationService.getBaseLanguageCode(result.getDetectedLanguage()) : "";

            // Save original transcript
            String originalTranscriptJson = objectMapper.writeValueAsString(result);
            String originalTranscriptName = baseName + languageSuffix + "_transcript.json";
            String originalTranscriptUrl = blobStorageService.saveTranscriptionResult(
                    originalTranscriptName, originalTranscriptJson);
            job.setOriginalTranscriptUrl(originalTranscriptUrl);
            logger.info("✓ Original transcript saved as: {}", originalTranscriptName);

            // Save English translation separately if needed and configured
            if (needsTranslation && keepOriginalLanguage) {
                TranscriptionResult englishResult = createEnglishOnlyResult(result, segments);
                String englishTranscriptJson = objectMapper.writeValueAsString(englishResult);
                String englishTranscriptName = baseName + "_en_transcript.json";
                String englishTranscriptUrl = blobStorageService.saveTranscriptionResult(
                        englishTranscriptName, englishTranscriptJson);
                job.setTranslatedTranscriptUrl(englishTranscriptUrl);
                logger.info("✓ English transcript saved as: {}", englishTranscriptName);
            }

            // Complete Job
            job.setStatus(TranscriptionJob.JobStatus.COMPLETED);
            job.setProgress(100);
            job.setCompletedAt(LocalDateTime.now());
            job.setMessage("Transcription completed successfully!");

            logger.info("=".repeat(80));
            logger.info("JOB COMPLETED SUCCESSFULLY");
            logger.info("Job ID: {}", job.getJobId());
            logger.info("Detected Language: {}", job.getDetectedLanguage());
            logger.info("Is English: {}", job.isEnglishAudio());
            logger.info("Translated: {}", job.isTranslated());
            logger.info("Total Segments: {}", segments.size());
            logger.info("Total Speakers: {}", job.getTotalSpeakers());
            logger.info("=".repeat(80));

            // Send email notification if provided
            if (job.getEmail() != null && !job.getEmail().isEmpty()) {
                emailService.sendCompletionNotification(job);
            }

        } catch (Exception e) {
            logger.error("!" .repeat(80));
            logger.error("JOB FAILED: {}", job.getJobId());
            logger.error("Error: {}", e.getMessage());
            logger.error("Stack trace:", e);
            logger.error("!" .repeat(80));

            job.setStatus(TranscriptionJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setMessage("Transcription failed: " + e.getMessage());

            if (job.getEmail() != null && !job.getEmail().isEmpty()) {
                emailService.sendFailureNotification(job, e.getMessage());
            }
        } finally {
            if (sasUrl != null && !sasUrl.contains("?")) {
                try {
                    blobStorageService.deleteFile(sasUrl);
                } catch (Exception e) {
                    logger.error("Error cleaning up blob: {}", e.getMessage());
                }
            }
        }
    }

    private String createTranscription(String audioUrlWithSas, String jobId, String language) throws IOException {
        String endpoint = "https://" + speechConfig.getRegion() +
                ".api.cognitive.microsoft.com/speechtotext/v3.1/transcriptions";

        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", speechConfig.getKey());
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            Map<String, Object> transcriptionDefinition = new HashMap<>();
            transcriptionDefinition.put("contentUrls", Collections.singletonList(audioUrlWithSas));

            if (language != null && !language.isEmpty() && !"auto".equals(language)) {
                transcriptionDefinition.put("locale", language);
                logger.info("Using specified language: {}", language);
            } else {
                transcriptionDefinition.put("locale", "en-US");
                logger.info("Using auto-language detection");
            }

            transcriptionDefinition.put("displayName", "SpeakerDiarization_" + jobId + "_" +
                    new Date().toString());

            Map<String, Object> properties = new HashMap<>();
            properties.put("diarizationEnabled", true);
            properties.put("channels", new int[]{0});
            properties.put("punctuationMode", "DictatedAndAutomatic");
            properties.put("profanityFilterMode", "Masked");
            properties.put("wordLevelTimestampsEnabled", true);

            transcriptionDefinition.put("properties", properties);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = objectMapper.writeValueAsBytes(transcriptionDefinition);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == 201) {
                return connection.getHeaderField("location");
            } else {
                String errorResponse = readErrorResponse(connection);
                throw new RuntimeException("Failed to create transcription. Code: " +
                        responseCode + ", Error: " + errorResponse);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TranscriptionResult pollForTranscriptionResult(String transcriptionUrl, TranscriptionJob job)
            throws IOException, InterruptedException {

        int attempt = 0;

        while (attempt < maxPollingAttempts) {
            attempt++;
            HttpURLConnection connection = null;

            try {
                URL url = new URL(transcriptionUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Ocp-Apim-Subscription-Key", speechConfig.getKey());

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    Map<String, Object> response = objectMapper.readValue(
                            connection.getInputStream(), Map.class);
                    String status = (String) response.get("status");

                    int progressBase = 30;
                    int progressRange = 30;
                    int estimatedProgress = progressBase + (attempt * progressRange / maxPollingAttempts);
                    job.setProgress(Math.min(estimatedProgress, 60));

                    if (attempt % 10 == 0 || attempt == 1) {
                        logger.info("   Polling attempt {}/{}: Status = {}",
                                attempt, maxPollingAttempts, status);
                    }

                    if ("Succeeded".equals(status)) {
                        logger.info("✓ Azure job completed after {} attempts", attempt);
                        Map<String, Object> links = (Map<String, Object>) response.get("links");
                        String filesUrl = (String) links.get("files");
                        return downloadTranscriptionFiles(filesUrl);

                    } else if ("Failed".equals(status)) {
                        throw new RuntimeException("Transcription failed");
                    }
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            TimeUnit.SECONDS.sleep(statusUpdateInterval);
        }

        throw new RuntimeException("Transcription timed out");
    }

    private TranscriptionResult downloadTranscriptionFiles(String filesUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(filesUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", speechConfig.getKey());

            Map<String, Object> filesResponse = objectMapper.readValue(
                    connection.getInputStream(), Map.class);

            List<Map<String, Object>> values = (List<Map<String, Object>>) filesResponse.get("values");

            for (Map<String, Object> file : values) {
                String kind = (String) file.get("kind");
                if ("Transcription".equals(kind)) {
                    Map<String, Object> links = (Map<String, Object>) file.get("links");
                    String contentUrl = (String) links.get("contentUrl");
                    return downloadTranscriptionContent(contentUrl);
                }
            }

            throw new RuntimeException("No transcription file found");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TranscriptionResult downloadTranscriptionContent(String contentUrl) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(contentUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            return objectMapper.readValue(connection.getInputStream(), TranscriptionResult.class);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<SpeakerSegment> parseTranscriptionResult(TranscriptionResult result) {
        List<SpeakerSegment> segments = new ArrayList<>();

        if (result.getRecognizedPhrases() != null) {
            for (TranscriptionResult.RecognizedPhrase phrase : result.getRecognizedPhrases()) {
                if (phrase.getNBest() != null && !phrase.getNBest().isEmpty()) {
                    TranscriptionResult.NBest best = phrase.getNBest().get(0);

                    String speakerId = "Speaker " + phrase.getSpeaker();
                    String text = best.getDisplay(); // Original text in detected language
                    long offset = phrase.getOffsetInTicks() / 10000;
                    long duration = phrase.getDurationInTicks() / 10000;
                    double confidence = best.getConfidence();

                    // Create segment with original text (translated will be added later if needed)
                    SpeakerSegment segment = new SpeakerSegment(
                            speakerId, text, offset, duration, confidence);
                    segments.add(segment);
                }
            }
        }

        segments.sort(Comparator.comparingLong(SpeakerSegment::getOffset));
        return segments;
    }

    private TranscriptionResult createEnglishOnlyResult(TranscriptionResult original, List<SpeakerSegment> segments) {
        TranscriptionResult englishResult = new TranscriptionResult();
        englishResult.setSource(original.getSource());
        englishResult.setTimestamp(original.getTimestamp());
        englishResult.setDuration(original.getDuration());
        englishResult.setDurationInTicks(original.getDurationInTicks());
        englishResult.setDetectedLanguage("en");
        englishResult.setTranslated(true);

        // Create new recognized phrases with English text
        List<TranscriptionResult.RecognizedPhrase> englishPhrases = new ArrayList<>();

        if (original.getRecognizedPhrases() != null) {
            for (int i = 0; i < original.getRecognizedPhrases().size(); i++) {
                TranscriptionResult.RecognizedPhrase originalPhrase = original.getRecognizedPhrases().get(i);
                SpeakerSegment segment = segments.get(i);

                TranscriptionResult.RecognizedPhrase englishPhrase = new TranscriptionResult.RecognizedPhrase();
                englishPhrase.setSpeaker(originalPhrase.getSpeaker());
                englishPhrase.setChannel(originalPhrase.getChannel());
                englishPhrase.setOffset(originalPhrase.getOffset());
                englishPhrase.setDuration(originalPhrase.getDuration());
                englishPhrase.setOffsetInTicks(originalPhrase.getOffsetInTicks());
                englishPhrase.setDurationInTicks(originalPhrase.getDurationInTicks());

                if (originalPhrase.getNBest() != null) {
                    List<TranscriptionResult.NBest> englishNBest = new ArrayList<>();
                    TranscriptionResult.NBest originalNBest = originalPhrase.getNBest().get(0);
                    TranscriptionResult.NBest englishNbest = new TranscriptionResult.NBest();

                    englishNbest.setConfidence(originalNBest.getConfidence());
                    englishNbest.setLexical(originalNBest.getLexical());
                    englishNbest.setItn(originalNBest.getItn());
                    englishNbest.setMaskedITN(originalNBest.getMaskedITN());
                    englishNbest.setDisplay(segment.getTranslatedText()); // Use translated text

                    englishNBest.add(englishNbest);
                    englishPhrase.setNBest(englishNBest);
                }

                englishPhrases.add(englishPhrase);
            }
        }

        englishResult.setRecognizedPhrases(englishPhrases);
        return englishResult;
    }

    private String readErrorResponse(HttpURLConnection connection) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    private void updateJobProgress(TranscriptionJob job, TranscriptionJob.JobStatus status,
                                   int progress, String message) {
        job.setStatus(status);
        job.setProgress(progress);
        job.setMessage(message);
        logger.info("[{}%] {}", progress, message);
    }

    public TranscriptionJob getJob(String jobId) {
        return jobStore.get(jobId);
    }

    public List<TranscriptionJob> getRecentJobs(int limit) {
        return jobStore.values().stream()
                .sorted((j1, j2) -> j2.getCreatedAt().compareTo(j1.getCreatedAt()))
                .limit(limit)
                .toList();
    }

    public static class SpeakerSegment {
        private String speakerId;
        private String text;           // Original language text
        private String translatedText;  // English translation (for non-English audio)
        private long offset;
        private long duration;
        private double confidence;

        public SpeakerSegment(String speakerId, String text, long offset, long duration, double confidence) {
            this.speakerId = speakerId;
            this.text = text;
            this.offset = offset;
            this.duration = duration;
            this.confidence = confidence;
            this.translatedText = text; // Default to same as original
        }

        // Getters and Setters
        public String getSpeakerId() { return speakerId; }
        public void setSpeakerId(String speakerId) { this.speakerId = speakerId; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getTranslatedText() { return translatedText; }
        public void setTranslatedText(String translatedText) { this.translatedText = translatedText; }

        public long getOffset() { return offset; }
        public void setOffset(long offset) { this.offset = offset; }

        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public String getFormattedTime() {
            long seconds = offset / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            long millis = offset % 1000;
            return String.format("%02d:%02d.%03d", minutes, seconds, millis);
        }

        // Helper to check if this segment has translation (different from original)
        public boolean hasTranslation() {
            return translatedText != null && !translatedText.equals(text);
        }
    }
}