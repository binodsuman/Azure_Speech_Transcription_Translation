package com.example.speechapp.controller;

import com.example.speechapp.model.TranscriptionJob;
import com.example.speechapp.service.SpeechService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Binod Suman
 *
 * Purpose of SpeechController.java
 * SpeechController.java is the web controller/REST controller that handles all HTTP requests, manages user
 * interactions, and orchestrates the flow between the UI and backend services. It serves as the entry point
 * for all client communications.
 *
 * Detailed Explanation
 * Primary Purpose
 * The controller acts as the presentation layer that:
 *
 * Serves web pages (UI endpoints)
 * Handles file uploads
 * Provides REST APIs for asynchronous status checking
 * Manages request validation and error handling
 * Routes requests to appropriate services
 *
 * Prepares data for view templates
 */


import com.example.speechapp.model.TranscriptionJob;
import com.example.speechapp.service.BlobStorageService;
import com.example.speechapp.service.SpeechService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling web and API requests.
 * Manages file uploads, job status, and language preferences.
 *
 * Purpose:
 * - Serve web pages (upload, status, jobs)
 * - Handle file upload with language options
 * - Provide REST APIs for status checking
 * - Manage language detection and translation preferences
 */
@Controller
public class SpeechController {

    private static final Logger logger = LoggerFactory.getLogger(SpeechController.class);

    @Autowired
    private SpeechService speechService;

    @Autowired
    private BlobStorageService blobStorageService;

    @Value("${transcription.keep-original-language:true}")
    private boolean defaultKeepOriginalLanguage;

    @Value("${transcription.target-language:en}")
    private String defaultTargetLanguage;

    /**
     * Serves the upload page with language options.
     *
     * @param model Spring MVC model
     * @return Upload page view
     */
    @GetMapping("/")
    public String index(Model model) {
        logger.debug("Accessing upload page");

        // Add language options to model
        model.addAttribute("keepOriginalLanguage", defaultKeepOriginalLanguage);
        model.addAttribute("targetLanguage", defaultTargetLanguage);
        model.addAttribute("languages", getSupportedLanguages());

        // Get list of processed files
        List<BlobStorageService.ProcessedFileInfo> processedFiles =
                blobStorageService.getProcessedFiles();
        model.addAttribute("processedFiles", processedFiles);

        return "upload";
    }

    /**
     * Handles file upload with language preferences.
     *
     * @param file Uploaded WAV file
     * @param email Optional email for notification
     * @param keepOriginal Whether to keep original language transcript
     * @param targetLanguage Target language for translation
     * @param redirectAttributes For flash messages
     * @return Redirect to status page or back to upload
     */
    @PostMapping("/upload")
    public String handleFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "keepOriginalLanguage", defaultValue = "true") boolean keepOriginal,
            @RequestParam(value = "targetLanguage", defaultValue = "en") String targetLanguage,
            RedirectAttributes redirectAttributes) {

        logger.info("Received file upload request: {}", file.getOriginalFilename());
        logger.info("Language preferences - Keep original: {}, Target: {}", keepOriginal, targetLanguage);

        try {
            if (file.isEmpty()) {
                logger.warn("Upload attempt with empty file");
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/";
            }

            String contentType = file.getContentType();
            logger.debug("File content type: {}", contentType);

            if (contentType == null || !contentType.equals("audio/wav")) {
                logger.warn("Invalid file type: {}", contentType);
                redirectAttributes.addFlashAttribute("error", "Please upload a WAV file");
                return "redirect:/";
            }

            if (file.getSize() > 200 * 1024 * 1024) {
                logger.warn("File too large: {} bytes", file.getSize());
                redirectAttributes.addFlashAttribute("error", "File size exceeds 200MB limit");
                return "redirect:/";
            }

            logger.info("Starting transcription job for file: {}", file.getOriginalFilename());
            TranscriptionJob job = speechService.startTranscriptionJob(file, email);

            // Override default language preferences if provided
            job.setKeepOriginalLanguage(keepOriginal);
            job.setTranslationTargetLanguage(targetLanguage);

            logger.info("Job created with ID: {}", job.getJobId());
            return "redirect:/status/" + job.getJobId();

        } catch (Exception e) {
            logger.error("Error processing upload: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/";
        }
    }

    /**
     * Displays job status page.
     *
     * @param jobId Job ID
     * @param model Spring MVC model
     * @return Status page view
     */
    @GetMapping("/status/{jobId}")
    public String getJobStatus(@PathVariable String jobId, Model model) {
        logger.debug("Viewing status for job: {}", jobId);

        TranscriptionJob job = speechService.getJob(jobId);

        if (job == null) {
            logger.warn("Job not found: {}", jobId);
            model.addAttribute("error", "Job not found");
            return "upload";
        }

        logger.debug("Job status: {}, progress: {}", job.getStatus(), job.getProgress());
        model.addAttribute("job", job);

        if (job.getStatus() == TranscriptionJob.JobStatus.COMPLETED) {
            model.addAttribute("segments", job.getSegments());
            logger.info("Job {} completed with {} segments", jobId,
                    job.getSegments() != null ? job.getSegments().size() : 0);
        }

        return "status";
    }

    /**
     * API endpoint to check file existence.
     *
     * @param file File to check
     * @return JSON with existence status
     */
    @PostMapping("/api/check-file")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkFileExistence(
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> response = new HashMap<>();
        String filename = file.getOriginalFilename();

        boolean exists = blobStorageService.fileExists(filename);
        response.put("exists", exists);
        response.put("filename", filename);

        if (exists) {
            // Check for language-specific transcripts
            String baseName = filename.replace(".wav", "");
            String[] languageSuffixes = {"en", "es", "fr", "de", "it", "pt", "ru", "zh", "ja", "ko"};

            List<Map<String, String>> availableTranscripts = new java.util.ArrayList<>();
            for (String lang : languageSuffixes) {
                String transcriptName = baseName + "_" + lang + "_transcript.json";
                if (blobStorageService.transcriptExists(transcriptName)) {
                    Map<String, String> transcript = new HashMap<>();
                    transcript.put("language", lang);
                    transcript.put("url", "/api/transcript/" + transcriptName);
                    availableTranscripts.add(transcript);
                }
            }
            response.put("availableTranscripts", availableTranscripts);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * API endpoint to get transcript JSON.
     *
     * @param filename Transcript filename
     * @return JSON transcript
     */
    @GetMapping("/api/transcript/{filename}")
    @ResponseBody
    public ResponseEntity<String> getTranscript(@PathVariable String filename) {
        String content = blobStorageService.getTranscriptContent(filename);
        if (content == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .body(content);
    }

    /**
     * API endpoint to get job status as JSON.
     *
     * @param jobId Job ID
     * @return JSON with job status
     */
    @GetMapping("/api/status/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getJobStatusApi(@PathVariable String jobId) {
        logger.debug("API status request for job: {}", jobId);

        TranscriptionJob job = speechService.getJob(jobId);

        if (job == null) {
            logger.warn("API: Job not found: {}", jobId);
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getJobId());
        response.put("status", job.getStatus().toString());
        response.put("message", job.getMessage());
        response.put("progress", job.getProgress());
        response.put("fileName", job.getFileName());
        response.put("createdAt", job.getCreatedAt());
        response.put("detectedLanguage", job.getDetectedLanguage());
        response.put("translated", job.isTranslated());

        if (job.getStatus() == TranscriptionJob.JobStatus.COMPLETED) {
            response.put("segments", job.getSegments());
            response.put("completedAt", job.getCompletedAt());
            response.put("totalSpeakers", job.getTotalSpeakers());
            response.put("totalDuration", job.getTotalDuration());
            response.put("originalTranscriptUrl", job.getOriginalTranscriptUrl());
            response.put("translatedTranscriptUrl", job.getTranslatedTranscriptUrl());
            logger.debug("API: Job {} completed", jobId);
        } else if (job.getStatus() == TranscriptionJob.JobStatus.FAILED) {
            response.put("error", job.getErrorMessage());
            logger.debug("API: Job {} failed", jobId);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Displays list of recent jobs.
     *
     * @param model Spring MVC model
     * @return Jobs page view
     */
    @GetMapping("/jobs")
    public String listJobs(Model model) {
        logger.debug("Listing recent jobs");

        List<TranscriptionJob> recentJobs = speechService.getRecentJobs(20);
        model.addAttribute("jobs", recentJobs);

        logger.info("Found {} recent jobs", recentJobs.size());
        return "jobs";
    }

    /**
     * API endpoint to list all jobs.
     *
     * @return List of jobs as JSON
     */
    @GetMapping("/api/jobs")
    @ResponseBody
    public ResponseEntity<List<TranscriptionJob>> listJobsApi() {
        List<TranscriptionJob> recentJobs = speechService.getRecentJobs(20);
        return ResponseEntity.ok(recentJobs);
    }

    /**
     * Gets supported languages for UI.
     *
     * @return Map of language codes to display names
     */
    private Map<String, String> getSupportedLanguages() {
        Map<String, String> languages = new HashMap<>();
        languages.put("en", "English");
        languages.put("es", "Spanish");
        languages.put("fr", "French");
        languages.put("de", "German");
        languages.put("it", "Italian");
        languages.put("pt", "Portuguese");
        languages.put("ru", "Russian");
        languages.put("zh", "Chinese");
        languages.put("ja", "Japanese");
        languages.put("ko", "Korean");
        languages.put("ar", "Arabic");
        languages.put("hi", "Hindi");
        return languages;
    }
}