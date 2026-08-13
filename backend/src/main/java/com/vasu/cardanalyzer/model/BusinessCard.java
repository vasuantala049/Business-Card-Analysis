package com.vasu.cardanalyzer.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Document(collection = "business_cards")
public class BusinessCard {

    @Id
    private String id;

    private String name;
    private String designation;
    private String company;
    private List<String> phones;
    private List<String> emails;
    private String website;
    private String address;

    private String rawOcrText;
    private String logoImageBase64;
    private double confidence;
    private String extractionSource;

    private Instant createdAt = Instant.now();
}
