package com.vasu.cardanalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MlExtractionRequest {

    @JsonProperty("image_base64")
    private String imageBase64;

    @JsonProperty("content_type")
    private String contentType;

    private String filename;
}