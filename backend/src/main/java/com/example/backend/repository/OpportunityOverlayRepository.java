package com.example.backend.repository;

import com.example.backend.entity.OpportunityOverlay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityOverlayRepository extends MongoRepository<OpportunityOverlay, String> {

    List<OpportunityOverlay> findByOpportunityId(String opportunityId);

    List<OpportunityOverlay> findByOpportunityIdAndOpportunityRoleIdAndEmployeeId(
            String opportunityId,
            String opportunityRoleId,
            String employeeId
    );

    void deleteByOpportunityId(String opportunityId);

    Optional<OpportunityOverlay> findByOverlayId(String overlayId);
}
