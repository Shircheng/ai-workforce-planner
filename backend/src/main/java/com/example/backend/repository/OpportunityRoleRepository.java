package com.example.backend.repository;

import com.example.backend.entity.OpportunityRole;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OpportunityRoleRepository extends MongoRepository<OpportunityRole, String> {

    List<OpportunityRole> findByOpportunityId(String opportunityId);
}
