package com.example.backend.repository;

import com.example.backend.entity.OpportunityOverlay;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityOverlayRepository extends MongoRepository<OpportunityOverlay, String> {

    Optional<OpportunityOverlay> findByOverlayId(String overlayId);
}
