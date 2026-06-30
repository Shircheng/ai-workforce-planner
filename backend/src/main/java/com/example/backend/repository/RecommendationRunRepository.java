package com.example.backend.repository;

import com.example.backend.entity.RecommendationRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationRunRepository extends MongoRepository<RecommendationRun, String> {

    Optional<RecommendationRun> findByRecommendationRunId(String recommendationRunId);

    Optional<RecommendationRun> findTopByOpportunityIdOrderByGeneratedAtDesc(String opportunityId);

    List<RecommendationRun> findByOpportunityIdOrderByGeneratedAtDesc(String opportunityId);
}
