package com.vasu.cardanalyzer.repository;

import com.vasu.cardanalyzer.model.BusinessCard;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BusinessCardRepository extends MongoRepository<BusinessCard, String> {
}
