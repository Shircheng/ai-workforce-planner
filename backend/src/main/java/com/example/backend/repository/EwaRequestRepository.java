package com.example.backend.repository;

import com.example.backend.entity.EwaRequest;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EwaRequestRepository extends MongoRepository<EwaRequest, String> {

    List<EwaRequest> findByOpportunityIdAndOpportunityRoleIdAndEmployeeId(
            String opportunityId,
            String opportunityRoleId,
            String employeeId
    );
}
