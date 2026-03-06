package com.example.speechapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Azure Translator Service.
 * Holds credentials and settings for translating non-English transcripts to English.
 *
 * Purpose:
 * - Stores Translator API key and region
 * - Enables translation of transcribed text to target language
 * - Supports multi-language transcription workflow
 */
@Configuration
@ConfigurationProperties(prefix = "azure.translator")
public class TranslatorConfig {

    /**
     * Azure Translator API key
     * Get from: Azure Portal -> Translator Service -> Keys and Endpoint
     */
    private String key;

    /**
     * Azure region for Translator service
     * Should match the region where Translator resource is created
     */
    private String region;

    /**
     * Translator service endpoint URL
     * Default: https://api.cognitive.microsofttranslator.com/
     */
    private String endpoint;

    // Getters and Setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
}