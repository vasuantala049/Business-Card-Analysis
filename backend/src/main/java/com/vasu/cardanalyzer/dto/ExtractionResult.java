package com.vasu.cardanalyzer.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionResult {

    @JsonProperty("raw_text")
    private String rawText;

    private Fields fields;

    @JsonProperty("logo_image")
    private String logoImage;

    @JsonProperty("extraction_source")
    private String extractionSource;

    private double confidence;

    @JsonProperty("ocr_variants_debug")
    private List<OcrVariantDebug> ocrVariantsDebug;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        private String name;
        private String designation;
        private String company;
        private List<String> phones;
        private List<String> emails;
        private String website;
        private String address;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OcrVariantDebug {
        private String variant;
        private String text;
        private double score;

        @JsonProperty("avg_confidence")
        private double avgConfidence;
    }
}
