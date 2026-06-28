package com.example.backend.repository;

import com.example.backend.entity.Opportunity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityRepository extends MongoRepository<Opportunity, String> {
}
