package com.vasu.cardanalyzer.model;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "business_cards")
public class BusinessCard {

    @Id
    private String id;

    @org.springframework.data.mongodb.core.index.Indexed
    private String ownerId;

    private String name;
    private String designation;
    private String company;
    private List<String> phones;
    private List<String> emails;
    private String website;
    private String address;

    private String rawOcrText;
    private String originalImageBase64;
    private String logoImageBase64;
    private double confidence;
    private String extractionSource;

    private Instant createdAt = Instant.now();
}
