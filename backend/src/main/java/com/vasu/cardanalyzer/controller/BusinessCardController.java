package com.vasu.cardanalyzer.controller;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vasu.cardanalyzer.dto.ExtractionResult;
import com.vasu.cardanalyzer.model.BusinessCard;
import com.vasu.cardanalyzer.repository.BusinessCardRepository;
import com.vasu.cardanalyzer.security.UserPrincipal;
import com.vasu.cardanalyzer.service.BusinessCardDuplicateService;
import com.vasu.cardanalyzer.service.MlServiceClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class BusinessCardController {

    private final MlServiceClient mlServiceClient;
    private final BusinessCardRepository repository;
    private final BusinessCardDuplicateService duplicateService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<BusinessCard> upload(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ExtractionResult result = mlServiceClient.extract(file);

        byte[] originalImageBytes;
        try {
            originalImageBytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded file", e);
        }

        BusinessCard card = new BusinessCard();
        card.setOwnerId(currentUser.getId());
        card.setRawOcrText(result.getRawText());
        card.setOriginalImageBase64(Base64.getEncoder().encodeToString(originalImageBytes));
        card.setLogoImageBase64(result.getLogoImage());
        card.setConfidence(result.getConfidence());
        card.setExtractionSource(result.getExtractionSource());

        if (result.getFields() != null) {
            card.setName(result.getFields().getName());
            card.setDesignation(result.getFields().getDesignation());
            card.setCompany(result.getFields().getCompany());
            card.setPhones(result.getFields().getPhones());
            card.setEmails(result.getFields().getEmails());
            card.setWebsite(result.getFields().getWebsite());
            card.setAddress(result.getFields().getAddress());
        }

        duplicateService.assertNotDuplicate(card, currentUser.getId(), null);

        return ResponseEntity.ok(repository.save(card));
    }

    @GetMapping
    public ResponseEntity<List<BusinessCard>> list(@AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(repository.findByOwnerId(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BusinessCard> get(@PathVariable String id,
                                           @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return repository.findByIdAndOwnerId(id, currentUser.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<BusinessCard> update(@PathVariable String id,
                                               @RequestBody BusinessCard updated,
                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return repository.findByIdAndOwnerId(id, currentUser.getId())
                .map(existing -> {
                    updated.setId(id);
                    updated.setOwnerId(currentUser.getId());
                    updated.setCreatedAt(existing.getCreatedAt());
                    duplicateService.assertNotDuplicate(updated, currentUser.getId(), id);
                    return ResponseEntity.ok(repository.save(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return repository.findByIdAndOwnerId(id, currentUser.getId())
                .map(card -> {
                    repository.delete(card);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
