package com.vasu.cardanalyzer.repository;

import com.vasu.cardanalyzer.model.BusinessCard;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessCardRepository extends MongoRepository<BusinessCard, String> {

    List<BusinessCard> findByOwnerId(String ownerId);

    Optional<BusinessCard> findByIdAndOwnerId(String id, String ownerId);
}
