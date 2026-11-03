package com.vasu.cardanalyzer.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.vasu.cardanalyzer.exception.DuplicateBusinessCardException;
import com.vasu.cardanalyzer.model.BusinessCard;
import com.vasu.cardanalyzer.repository.BusinessCardRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessCardDuplicateService {

    private final BusinessCardRepository repository;

    public void assertNotDuplicate(BusinessCard candidate, String ownerId, String excludedCardId) {
        if (candidate == null || ownerId == null || ownerId.isBlank()) {
            return;
        }

        for (BusinessCard existing : repository.findByOwnerId(ownerId)) {
            if (existing == null || existing.getOwnerId() == null || !ownerId.equals(existing.getOwnerId())) {
                continue;
            }

            if (excludedCardId != null && excludedCardId.equals(existing.getId())) {
                continue;
            }

            List<String> reasons = duplicateReasons(candidate, existing);
            if (!reasons.isEmpty()) {
                throw new DuplicateBusinessCardException(
                        existing.getId(),
                        existing.getName(),
                        existing.getCompany(),
                        reasons
                );
            }
        }
    }

    private List<String> duplicateReasons(BusinessCard candidate, BusinessCard existing) {
        Set<String> reasons = new LinkedHashSet<>();

        if (sharesNormalizedValue(normalizedEmails(candidate.getEmails()), normalizedEmails(existing.getEmails()))) {
            reasons.add("matching email");
        }

        if (sharesNormalizedValue(normalizedPhones(candidate.getPhones()), normalizedPhones(existing.getPhones()))) {
            reasons.add("matching phone number");
        }

        String candidateName = normalizeText(candidate.getName());
        String candidateCompany = normalizeText(candidate.getCompany());
        String existingName = normalizeText(existing.getName());
        String existingCompany = normalizeText(existing.getCompany());

        if (!candidateName.isBlank() && !candidateCompany.isBlank()
                && candidateName.equals(existingName)
                && candidateCompany.equals(existingCompany)) {
            reasons.add("same name and company");
        }

        return new ArrayList<>(reasons);
    }

    private boolean sharesNormalizedValue(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizedEmails(List<String> values) {
        return normalizeValues(values).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizedPhones(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();

        for (String value : normalizeValues(values)) {
            String digits = value.replaceAll("\\D", "");
            if (digits.isBlank()) {
                continue;
            }

            normalized.add(digits);
            if (digits.length() > 10) {
                normalized.add(digits.substring(digits.length() - 10));
            }
        }

        return normalized;
    }

    private Set<String> normalizeValues(List<String> values) {
        if (values == null) {
            return Set.of();
        }

        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}