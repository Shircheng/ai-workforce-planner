package com.example.backend.repository;

import com.example.backend.entity.OpportunityOverlay;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityOverlayRepository extends MongoRepository<OpportunityOverlay, String> {

    List<OpportunityOverlay> findByOpportunityId(String opportunityId);

    void deleteByOpportunityId(String opportunityId);
}
