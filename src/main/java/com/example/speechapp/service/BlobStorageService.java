package com.example.speechapp.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.example.speechapp.config.AzureBlobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.io.*;


/**
 * @author  Binod Suman
 *
 * Purpose of BlobStorageService.java
 * BlobStorageService.java is a service class that handles all interactions with Azure Blob Storage.
 * It acts as an abstraction layer between the application and Azure's storage SDK, providing a clean
 * interface for file operations.
 *
 * Detailed Explanation
 * Primary Purpose
 * The service encapsulates all blob storage operations:
 *
 * Uploading audio files to Azure Blob Storage
 * Generating SAS (Shared Access Signature) URLs for secure access
 * Checking for existing files and transcripts
 * Saving and retrieving transcription results
 * Managing file cleanup
 * Listing processed files
 */





/**
 * Service class for Azure Blob Storage operations.
 * Handles all file storage, retrieval, and management.
 *
 * Purpose:
 * - Upload audio files with original names
 * - Generate SAS URLs for secure access
 * - Check for existing files and transcripts
 * - Save and retrieve transcription results
 * - List processed files with metadata
 */
@Service
public class BlobStorageService {

    private static final Logger logger = LoggerFactory.getLogger(BlobStorageService.class);

    @Autowired
    private AzureBlobConfig blobConfig;

    private BlobContainerClient containerClient;

    /**
     * Initializes the blob container client.
     * Creates container if it doesn't exist.
     */
    private void initializeClient() {
        if (containerClient == null) {
            logger.info("Initializing Blob Storage client with connection string");
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(blobConfig.getConnectionString())
                    .buildClient();

            String containerName = blobConfig.getContainerName();
            containerClient = blobServiceClient.getBlobContainerClient(containerName);

            if (!containerClient.exists()) {
                logger.info("Creating container: {}", containerName);
                containerClient.create();
                logger.info("Container created successfully");
            } else {
                logger.info("Using existing container: {}", containerName);
            }
        }
    }

    /**
     * Checks if a file exists in blob storage.
     *
     * @param filename Name of the file to check
     * @return true if file exists
     */
    public boolean fileExists(String filename) {
        initializeClient();
        String safeFilename = sanitizeFilename(filename);
        BlobClient blobClient = containerClient.getBlobClient(safeFilename);
        return blobClient.exists();
    }

    /**
     * Checks if a transcript exists for a file.
     *
     * @param filename Name of the transcript file
     * @return true if transcript exists
     */
    public boolean transcriptExists(String filename) {
        initializeClient();
        BlobClient blobClient = containerClient.getBlobClient(filename);
        return blobClient.exists();
    }

    /**
     * Gets transcript content from blob storage.
     *
     * @param filename Name of the transcript file
     * @return JSON content as string
     */
    public String getTranscriptContent(String filename) {
        initializeClient();
        BlobClient blobClient = containerClient.getBlobClient(filename);

        if (!blobClient.exists()) {
            return null;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            blobClient.downloadStream(outputStream);
            return outputStream.toString("UTF-8");
        } catch (Exception e) {
            logger.error("Error downloading transcript: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets list of all processed files (with transcripts).
     *
     * @return List of processed file information
     */
    public List<ProcessedFileInfo> getProcessedFiles() {
        initializeClient();
        List<ProcessedFileInfo> processedFiles = new ArrayList<>();

        for (BlobItem blobItem : containerClient.listBlobs()) {
            String blobName = blobItem.getName();
            if (blobName.endsWith("_transcript.json")) {
                // Extract language from filename
                String language = extractLanguageFromFilename(blobName);

                ProcessedFileInfo info = new ProcessedFileInfo();
                info.setTranscriptFile(blobName);
                info.setLastModified(blobItem.getProperties().getLastModified());
                info.setSize(blobItem.getProperties().getContentLength());
                info.setLanguage(language);

                // Try to find corresponding audio file
                String audioFile = blobName.replace("_transcript.json", ".wav");
                if (fileExists(audioFile)) {
                    info.setAudioFile(audioFile);
                }

                processedFiles.add(info);
            }
        }

        return processedFiles;
    }

    /**
     * Extracts language code from transcript filename.
     * Format: basename_XX_transcript.json where XX is language code
     *
     * @param filename Transcript filename
     * @return Language code or "unknown"
     */
    private String extractLanguageFromFilename(String filename) {
        String[] parts = filename.split("_");
        if (parts.length >= 2) {
            // Look for language code pattern (2 letters)
            for (String part : parts) {
                if (part.matches("[a-z]{2}")) {
                    return part;
                }
            }
        }
        return "unknown";
    }

    /**
     * Uploads a file and generates SAS URL.
     *
     * @param file File to upload
     * @param originalFilename Original filename
     * @return SAS URL for secure access
     * @throws IOException if upload fails
     */
    public String uploadFileAndGenerateSas(MultipartFile file, String originalFilename) throws IOException {
        logger.info("Starting file upload process for: {}", originalFilename);
        initializeClient();

        String safeFilename = sanitizeFilename(originalFilename);
        String blobName = safeFilename;

        logger.info("Uploading file as blob: {}", blobName);

        BlobClient blobClient = containerClient.getBlobClient(blobName);
        if (blobClient.exists()) {
            logger.info("File already exists, overwriting: {}", blobName);
        }

        // Set content type for WAV file
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType("audio/wav");

        try (InputStream inputStream = file.getInputStream()) {
            blobClient.upload(inputStream, file.getSize(), true);
            blobClient.setHttpHeaders(headers);
        }

        logger.info("File uploaded successfully. Size: {} bytes", file.getSize());

        // Generate SAS token
        BlobSasPermission sasPermission = new BlobSasPermission()
                .setReadPermission(true);

        OffsetDateTime expiryTime = OffsetDateTime.now().plus(Duration.ofHours(1));
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, sasPermission);

        String sasToken = blobClient.generateSas(sasValues);
        String fullUrl = blobClient.getBlobUrl() + "?" + sasToken;

        logger.info("SAS URL generated. Expires at: {}", expiryTime.format(DateTimeFormatter.ISO_DATE_TIME));

        return fullUrl;
    }

    /**
     * Saves transcription result to blob storage.
     *
     * @param filename Name of the transcript file
     * @param transcriptionJson JSON content
     * @return URL of the saved transcript
     */
    public String saveTranscriptionResult(String filename, String transcriptionJson) {
        logger.info("Saving transcription result: {}", filename);
        initializeClient();

        BlobClient blobClient = containerClient.getBlobClient(filename);

        // Set content type for JSON file
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType("application/json");

        byte[] jsonBytes = transcriptionJson.getBytes();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes)) {
            blobClient.upload(inputStream, jsonBytes.length, true);
            blobClient.setHttpHeaders(headers);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        logger.info("Transcript saved successfully");
        return blobClient.getBlobUrl();
    }

    /**
     * Deletes a file from blob storage.
     *
     * @param blobUrlWithSas URL of the file to delete (with or without SAS)
     * @return true if deleted successfully
     */
    public boolean deleteFile(String blobUrlWithSas) {
        logger.info("Attempting to delete blob: {}", blobUrlWithSas);
        try {
            initializeClient();
            String urlWithoutSas = blobUrlWithSas.contains("?")
                    ? blobUrlWithSas.substring(0, blobUrlWithSas.indexOf("?"))
                    : blobUrlWithSas;

            String blobName = urlWithoutSas.substring(urlWithoutSas.lastIndexOf("/") + 1);
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            boolean deleted = blobClient.deleteIfExists();
            if (deleted) {
                logger.info("Blob deleted successfully: {}", blobName);
            }
            return deleted;
        } catch (Exception e) {
            logger.error("Error deleting blob: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Sanitizes filename to be safe for blob storage.
     *
     * @param filename Original filename
     * @return Sanitized filename
     */
    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    /**
     * Inner class for processed file information.
     */
    public static class ProcessedFileInfo {
        private String audioFile;
        private String transcriptFile;
        private OffsetDateTime lastModified;
        private long size;
        private String language;

        public String getAudioFile() { return audioFile; }
        public void setAudioFile(String audioFile) { this.audioFile = audioFile; }

        public String getTranscriptFile() { return transcriptFile; }
        public void setTranscriptFile(String transcriptFile) { this.transcriptFile = transcriptFile; }

        public OffsetDateTime getLastModified() { return lastModified; }
        public void setLastModified(OffsetDateTime lastModified) { this.lastModified = lastModified; }

        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getFormattedSize() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }

        public String getFormattedDate() {
            return lastModified.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public String getLanguageDisplayName() {
            if (language == null) return "Unknown";
            switch (language.toLowerCase()) {
                case "en": return "English";
                case "es": return "Spanish";
                case "fr": return "French";
                case "de": return "German";
                case "it": return "Italian";
                case "pt": return "Portuguese";
                case "ru": return "Russian";
                case "zh": return "Chinese";
                case "ja": return "Japanese";
                case "ko": return "Korean";
                case "ar": return "Arabic";
                case "hi": return "Hindi";
                default: return language.toUpperCase();
            }
        }
    }
}