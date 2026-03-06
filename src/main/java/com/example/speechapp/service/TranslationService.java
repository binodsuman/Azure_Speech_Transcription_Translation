package com.example.speechapp.service;

import com.example.speechapp.config.TranslatorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for translating text using Azure Translator API.
 * Handles translation of transcribed speech from any language to target language (English).
 *
 * Purpose:
 * - Translate non-English transcripts to English
 * - Preserve original language if configured
 * - Support bilingual transcription storage
 * - Auto-detect source language
 */
@Service
public class TranslationService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);

    @Autowired
    private TranslatorConfig translatorConfig;

    @Value("${transcription.target-language:en}")
    private String targetLanguage;

    @Value("${transcription.keep-original-language:true}")
    private boolean keepOriginalLanguage;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Translates text from source language to target language using Azure Translator.
     *
     * @param text The text to translate
     * @param sourceLanguage Source language code (e.g., "es", "fr", "de") - can be null for auto-detect
     * @return Translated text in target language
     * @throws Exception if translation fails
     */
    public String translateText(String text, String sourceLanguage) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        logger.debug("Translating text from {} to {}",
                sourceLanguage != null ? sourceLanguage : "auto-detected", targetLanguage);

        String endpoint = translatorConfig.getEndpoint() + "translate?api-version=3.0&to=" + targetLanguage;

        // Add source language if provided (for auto-detection, omit to let Azure detect)
        if (sourceLanguage != null && !sourceLanguage.isEmpty() && !"auto".equals(sourceLanguage)) {
            // Extract base language code (e.g., "en-US" -> "en")
            String baseLanguage = sourceLanguage.split("-")[0];
            endpoint += "&from=" + baseLanguage;
            logger.debug("Using source language: {}", baseLanguage);
        } else {
            logger.debug("Using auto-language detection");
        }

        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", translatorConfig.getKey());
        connection.setRequestProperty("Ocp-Apim-Subscription-Region", translatorConfig.getRegion());
        connection.setDoOutput(true);

        // Create request body
        ObjectNode[] requestBody = new ObjectNode[] {
                objectMapper.createObjectNode().put("Text", text)
        };

        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        logger.trace("Translation request: {}", requestBodyJson);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = requestBodyJson.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine);
                }

                logger.trace("Translation response: {}", response.toString());

                // Parse response
                JsonNode jsonResponse = objectMapper.readTree(response.toString());
                if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                    JsonNode translations = jsonResponse.get(0).get("translations");
                    if (translations.isArray() && translations.size() > 0) {
                        String translatedText = translations.get(0).get("text").asText();
                        logger.debug("Translation successful");
                        return translatedText;
                    }
                }

                throw new RuntimeException("Unexpected translation response format");
            }
        } else {
            // Read error response
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {

                StringBuilder errorResponse = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    errorResponse.append(responseLine);
                }

                logger.error("Translation failed with code {}: {}", responseCode, errorResponse.toString());
                throw new RuntimeException("Translation failed: " + errorResponse.toString());
            }
        }
    }

    /**
     * Detects the language of the provided text using Azure Translator.
     *
     * @param text Text to detect language for
     * @return Detected language code (e.g., "en", "es", "fr")
     * @throws Exception if detection fails
     */
    public String detectLanguage(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return "unknown";
        }

        logger.debug("Detecting language for text sample");

        String endpoint = translatorConfig.getEndpoint() + "detect?api-version=3.0";

        URL url = new URL(endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Ocp-Apim-Subscription-Key", translatorConfig.getKey());
        connection.setRequestProperty("Ocp-Apim-Subscription-Region", translatorConfig.getRegion());
        connection.setDoOutput(true);

        // Create request body
        ObjectNode[] requestBody = new ObjectNode[] {
                objectMapper.createObjectNode().put("Text", text)
        };

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = objectMapper.writeValueAsBytes(requestBody);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

                JsonNode jsonResponse = objectMapper.readTree(br);
                if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                    String detectedLanguage = jsonResponse.get(0).get("language").asText();
                    logger.debug("Detected language: {}", detectedLanguage);
                    return detectedLanguage;
                }
            }
        }

        return "unknown";
    }

    /**
     * Checks if the detected language is English (or English variant).
     *
     * @param languageCode Language code to check
     * @return true if language is English
     */
    public boolean isEnglish(String languageCode) {
        return languageCode != null && languageCode.toLowerCase().startsWith("en");
    }

    /**
     * Extracts base language code (e.g., "en-US" -> "en").
     *
     * @param locale Language locale code
     * @return Base language code
     */
    public String getBaseLanguageCode(String locale) {
        if (locale == null || locale.isEmpty()) {
            return "unknown";
        }
        return locale.split("-")[0].toLowerCase();
    }
}