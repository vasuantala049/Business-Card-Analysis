package com.vasu.cardanalyzer.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vasu.cardanalyzer.exception.DuplicateBusinessCardException;
import com.vasu.cardanalyzer.model.BusinessCard;
import com.vasu.cardanalyzer.repository.BusinessCardRepository;

@ExtendWith(MockitoExtension.class)
class BusinessCardDuplicateServiceTest {

    @Mock
    private BusinessCardRepository repository;

    @InjectMocks
    private BusinessCardDuplicateService duplicateService;

    @Test
    void duplicateDetectionIgnoresCardsFromOtherOwners() {
        BusinessCard candidate = new BusinessCard();
        candidate.setName("Jane Doe");
        candidate.setCompany("Acme");
        candidate.setEmails(List.of("jane@example.com"));
        candidate.setPhones(List.of("+1 555 123 4567"));

        BusinessCard otherOwnerCard = new BusinessCard();
        otherOwnerCard.setId("other-card");
        otherOwnerCard.setOwnerId("owner-b");
        otherOwnerCard.setName("Jane Doe");
        otherOwnerCard.setCompany("Acme");
        otherOwnerCard.setEmails(List.of("jane@example.com"));
        otherOwnerCard.setPhones(List.of("+1 555 123 4567"));

        when(repository.findByOwnerId("owner-a")).thenReturn(List.of(otherOwnerCard));

        assertDoesNotThrow(() -> duplicateService.assertNotDuplicate(candidate, "owner-a", null));
    }

    @Test
    void duplicateDetectionStillFlagsSameOwnerDuplicates() {
        BusinessCard candidate = new BusinessCard();
        candidate.setName("Jane Doe");
        candidate.setCompany("Acme");
        candidate.setEmails(List.of("jane@example.com"));
        candidate.setPhones(List.of("+1 555 123 4567"));

        BusinessCard sameOwnerCard = new BusinessCard();
        sameOwnerCard.setId("same-card");
        sameOwnerCard.setOwnerId("owner-a");
        sameOwnerCard.setName("Jane Doe");
        sameOwnerCard.setCompany("Acme");
        sameOwnerCard.setEmails(List.of("jane@example.com"));
        sameOwnerCard.setPhones(List.of("+1 555 123 4567"));

        when(repository.findByOwnerId("owner-a")).thenReturn(List.of(sameOwnerCard));

        assertThrows(DuplicateBusinessCardException.class,
                () -> duplicateService.assertNotDuplicate(candidate, "owner-a", null));
    }
}
