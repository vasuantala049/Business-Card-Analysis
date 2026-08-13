package com.vasu.cardanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ExtractionResult {

    @JsonProperty("raw_text")
    private String rawText;

    private Fields fields;

    @JsonProperty("logo_image")
    private String logoImage;

    @JsonProperty("extraction_source")
    private String extractionSource;

    private double confidence;

    @Data
    public static class Fields {
        private String name;
        private String designation;
        private String company;
        private List<String> phones;
        private List<String> emails;
        private String website;
        private String address;
    }
}
