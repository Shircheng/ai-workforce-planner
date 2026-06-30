package com.example.backend.repository;

import com.example.backend.entity.Opportunity;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OpportunityRepository extends MongoRepository<Opportunity, String> {
    Optional<Opportunity> findByOpportunityId(String opportunityId);
}
