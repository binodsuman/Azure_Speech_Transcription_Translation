package com.example.speechapp.model;

import com.example.speechapp.service.SpeechService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job state management with enum states
 * Status tracking, progress updates, formatted file size
 * @author Binod Suman
 *
 * Purpose of TranscriptionJob.java
 * TranscriptionJob.java is a domain model class that tracks the state and progress of each transcription
 * request throughout its lifecycle. It serves as the application's internal representation of a transcription
 * task from start to finish.
 *
 * Detailed Explanation
 * Primary Purpose
 * The class maintains the complete state of a transcription job, allowing:
 *
 * Tracking progress from upload to completion
 *
 * Storing results for later retrieval
 * Managing job lifecycle (UPLOADING → PROCESSING → COMPLETED/FAILED)
 * Providing status updates to the UI
 * Persisting metadata for job history
 */


/**
 * Domain model class representing a transcription job.
 * Tracks the complete lifecycle and metadata of a transcription request.
 *
 * Purpose:
 * - Maintain job state and progress
 * - Store job results and metadata
 * - Track language information for multi-language support
 * - Provide formatted data for UI display
 */


import java.time.LocalDateTime;
import java.util.List;

public class TranscriptionJob {

    public enum JobStatus {
        UPLOADING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    private String jobId;
    private String fileName;
    private long fileSize;
    private JobStatus status;
    private String message;
    private int progress;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String azureTranscriptionUrl;
    private String blobSasUrl;
    private String email;
    private String errorMessage;

    // Language-related fields
    private String detectedLanguage;
    private boolean translated;
    private String originalLanguage;
    private String translationTargetLanguage;
    private boolean keepOriginalLanguage;
    private boolean isEnglishAudio;  // Flag to identify if audio is English

    // Results
    private List<SpeechService.SpeakerSegment> segments;
    private int totalSpeakers;
    private long totalDuration;

    // Transcript URLs
    private String originalTranscriptUrl;
    private String translatedTranscriptUrl;

    public TranscriptionJob() {
        this.createdAt = LocalDateTime.now();
        this.status = JobStatus.UPLOADING;
        this.progress = 0;
        this.message = "Job created";
    }

    // Getters and Setters
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getAzureTranscriptionUrl() { return azureTranscriptionUrl; }
    public void setAzureTranscriptionUrl(String azureTranscriptionUrl) { this.azureTranscriptionUrl = azureTranscriptionUrl; }

    public String getBlobSasUrl() { return blobSasUrl; }
    public void setBlobSasUrl(String blobSasUrl) { this.blobSasUrl = blobSasUrl; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDetectedLanguage() { return detectedLanguage; }
    public void setDetectedLanguage(String detectedLanguage) { this.detectedLanguage = detectedLanguage; }

    public boolean isTranslated() { return translated; }
    public void setTranslated(boolean translated) { this.translated = translated; }

    public String getOriginalLanguage() { return originalLanguage; }
    public void setOriginalLanguage(String originalLanguage) { this.originalLanguage = originalLanguage; }

    public String getTranslationTargetLanguage() { return translationTargetLanguage; }
    public void setTranslationTargetLanguage(String translationTargetLanguage) {
        this.translationTargetLanguage = translationTargetLanguage;
    }

    public boolean isKeepOriginalLanguage() { return keepOriginalLanguage; }
    public void setKeepOriginalLanguage(boolean keepOriginalLanguage) {
        this.keepOriginalLanguage = keepOriginalLanguage;
    }

    public boolean isEnglishAudio() { return isEnglishAudio; }
    public void setEnglishAudio(boolean englishAudio) { isEnglishAudio = englishAudio; }

    public List<SpeechService.SpeakerSegment> getSegments() { return segments; }

    public void setSegments(List<SpeechService.SpeakerSegment> segments) {
        this.segments = segments;
        if (segments != null && !segments.isEmpty()) {
            this.totalSpeakers = segments.stream()
                    .map(s -> s.getSpeakerId())
                    .distinct()
                    .collect(java.util.stream.Collectors.toSet())
                    .size();
            this.totalDuration = segments.stream()
                    .mapToLong(s -> s.getDuration())
                    .sum();
        }
    }

    public int getTotalSpeakers() { return totalSpeakers; }
    public long getTotalDuration() { return totalDuration; }

    public String getOriginalTranscriptUrl() { return originalTranscriptUrl; }
    public void setOriginalTranscriptUrl(String originalTranscriptUrl) {
        this.originalTranscriptUrl = originalTranscriptUrl;
    }

    public String getTranslatedTranscriptUrl() { return translatedTranscriptUrl; }
    public void setTranslatedTranscriptUrl(String translatedTranscriptUrl) {
        this.translatedTranscriptUrl = translatedTranscriptUrl;
    }

    public String getFormattedFileSize() {
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
    }

    public String getLanguageDisplayName() {
        if (detectedLanguage == null) return "Unknown";

        switch (detectedLanguage.toLowerCase()) {
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
            case "hi": return "Hindi";  // Added Hindi support
            default: return detectedLanguage.toUpperCase();
        }
    }
}