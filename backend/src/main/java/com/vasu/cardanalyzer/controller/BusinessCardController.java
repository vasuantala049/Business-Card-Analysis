package com.vasu.cardanalyzer.controller;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

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
import com.vasu.cardanalyzer.service.QwenServiceClient;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class BusinessCardController {

    private final QwenServiceClient qwenServiceClient;
    private final BusinessCardRepository repository;
    private final BusinessCardDuplicateService duplicateService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<BusinessCard> upload(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String contentType = file.getContentType();
        byte[] originalImageBytes;
        try {
            originalImageBytes = file.getBytes();
            byte[] resizedBytes = resizeImageIfNecessary(originalImageBytes, 512);
            if (resizedBytes != originalImageBytes) {
                contentType = "image/jpeg";
                originalImageBytes = resizedBytes;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded file", e);
        }

        String originalImageBase64 = Base64.getEncoder().encodeToString(originalImageBytes);
        ExtractionResult result = qwenServiceClient.extract(
                originalImageBase64,
                contentType,
                file.getOriginalFilename()
        );

        BusinessCard card = new BusinessCard();
        card.setOwnerId(currentUser.getId());
        card.setRawOcrText(result.getRawText());
        if (result.getOcrVariantsDebug() != null) {
            card.setOcrVariantsDebug(result.getOcrVariantsDebug().stream()
                    .map(debug -> {
                        BusinessCard.OcrVariantDebug cardDebug = new BusinessCard.OcrVariantDebug();
                        cardDebug.setVariant(debug.getVariant());
                        cardDebug.setText(debug.getText());
                        cardDebug.setScore(debug.getScore());
                        cardDebug.setAvgConfidence(debug.getAvgConfidence());
                        return cardDebug;
                    })
                    .collect(Collectors.toList()));
        }
                card.setOriginalImageBase64(originalImageBase64);
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

        return ResponseEntity.ok(card);
    }

    @PostMapping
    public ResponseEntity<BusinessCard> create(@RequestBody BusinessCard newCard,
                                               @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        newCard.setOwnerId(currentUser.getId());
        duplicateService.assertNotDuplicate(newCard, currentUser.getId(), null);
        return ResponseEntity.ok(repository.save(newCard));
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

    private byte[] resizeImageIfNecessary(byte[] imageBytes, int maxDimension) throws IOException {
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes)) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(bais);
            if (img == null) {
                return imageBytes;
            }
            int width = img.getWidth();
            int height = img.getHeight();
            if (width <= maxDimension && height <= maxDimension) {
                return imageBytes;
            }
            
            double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);
            
            java.awt.Image resultingImage = img.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
            java.awt.image.BufferedImage outputImage = new java.awt.image.BufferedImage(newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = outputImage.createGraphics();
            g2d.drawImage(resultingImage, 0, 0, null);
            g2d.dispose();
            
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                javax.imageio.ImageIO.write(outputImage, "jpg", baos);
                return baos.toByteArray();
            }
        }
    }
}
