package com.vasu.cardanalyzer.controller;

import com.vasu.cardanalyzer.dto.ExtractionResult;
import com.vasu.cardanalyzer.model.BusinessCard;
import com.vasu.cardanalyzer.repository.BusinessCardRepository;
import com.vasu.cardanalyzer.security.UserPrincipal;
import com.vasu.cardanalyzer.service.MlServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class BusinessCardController {

    private final MlServiceClient mlServiceClient;
    private final BusinessCardRepository repository;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<BusinessCard> upload(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ExtractionResult result = mlServiceClient.extract(file);

        BusinessCard card = new BusinessCard();
        card.setOwnerId(currentUser.getId());
        card.setRawOcrText(result.getRawText());
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
