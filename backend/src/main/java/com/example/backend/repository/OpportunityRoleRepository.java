package com.example.backend.repository;

import com.example.backend.entity.OpportunityRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityRoleRepository extends MongoRepository<OpportunityRole, String> {

    List<OpportunityRole> findByOpportunityId(String opportunityId);

    Optional<OpportunityRole> findByOpportunityRoleId(String opportunityRoleId);
}
