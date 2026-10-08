package com.vasu.cardanalyzer.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vasu.cardanalyzer.dto.ExtractionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QwenServiceClient {

    private static final String DEFAULT_MODEL = "qwen3-vl";

    private final WebClient qwenWebClient;
    private final ObjectMapper objectMapper;

    @Value("${qwen.model:" + DEFAULT_MODEL + "}")
    private String model;

    public ExtractionResult extract(String imageBase64, String contentType, String filename) {
        String safeContentType = (contentType == null || contentType.isBlank()) ? "image/jpeg" : contentType;
        String dataUrl = "data:" + safeContentType + ";base64," + imageBase64;

        QwenChatRequest request = new QwenChatRequest(
                model,
                0,
                1024,
                List.of(
                        new QwenMessage("system",
                                "You extract business card details from a single image and return compact JSON only."),
                        new QwenMessage(
                                "user",
                                List.of(
                                        new QwenContentText(
                                                "Read the business card image and return ONLY compact JSON with this exact shape: "
                                                        + "{\"raw_text\": string, \"fields\": {\"name\": string|null, \"designation\": string|null, "
                                                        + "\"company\": string|null, \"phones\": string[], \"emails\": string[], \"website\": string|null, "
                                                        + "\"address\": string|null}, \"logo_image\": null, \"extraction_source\": \"qwen\", "
                                                        + "\"confidence\": number, \"ocr_variants_debug\": []}. "
                                                        + "Do not put the base64 image data in the text response. Do not include markdown fences. "
                                                        + "If a field is missing, use null for that field and [] for missing lists. "
                                                        + "The raw_text field should contain the best plain-text transcription of the visible card text."),
                                        new QwenContentImage(dataUrl)
                                )
                        )
                )
        );

        QwenChatResponse response = qwenWebClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(QwenChatResponse.class)
                .block();

        if (response == null || response.choices == null || response.choices.isEmpty() || response.choices.get(0).message == null) {
            throw new IllegalStateException("Qwen returned an empty response");
        }

        String content = response.choices.get(0).message.content;
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Qwen returned an empty message");
        }

        String cleaned = content.trim();
        int jsonStart = cleaned.indexOf("```json");
        if (jsonStart != -1) {
            int jsonEnd = cleaned.lastIndexOf("```");
            if (jsonEnd > jsonStart) {
                cleaned = cleaned.substring(jsonStart + 7, jsonEnd).trim();
            }
        } else {
            int firstBrace = cleaned.indexOf('{');
            int lastBrace = cleaned.lastIndexOf('}');
            if (firstBrace != -1 && lastBrace > firstBrace) {
                cleaned = cleaned.substring(firstBrace, lastBrace + 1).trim();
            }
        }

        try {
            ExtractionResult result = objectMapper.readValue(cleaned, ExtractionResult.class);
            if (result.getExtractionSource() == null || result.getExtractionSource().isBlank()) {
                result.setExtractionSource("qwen");
            }
            if (result.getLogoImage() == null) {
                result.setLogoImage(null);
            }
            if (result.getOcrVariantsDebug() == null) {
                result.setOcrVariantsDebug(List.of());
            }
            if (result.getFields() == null) {
                result.setFields(new ExtractionResult.Fields());
            }
            if (result.getFields().getPhones() == null) {
                result.getFields().setPhones(List.of());
            }
            if (result.getFields().getEmails() == null) {
                result.getFields().setEmails(List.of());
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse Qwen JSON response: " + cleaned, e);
        }
    }

    record QwenChatRequest(String model, int temperature, Integer max_tokens, List<QwenMessage> messages) {}

    record QwenMessage(String role, Object content) {}

    record QwenContentText(String type, String text) {
        QwenContentText(String text) {
            this("text", text);
        }
    }

    record QwenContentImage(@JsonProperty("image_url") ImageUrl imageUrl, String type) {
        QwenContentImage(String url) {
            this(new ImageUrl(url), "image_url");
        }
    }

    record ImageUrl(String url) {}

    record QwenChatResponse(List<QwenChoice> choices) {}

    record QwenChoice(QwenResponseMessage message) {}

    record QwenResponseMessage(String content) {}
}