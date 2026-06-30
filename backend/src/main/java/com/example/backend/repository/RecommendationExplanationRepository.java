package com.example.backend.repository;

import com.example.backend.entity.RecommendationExplanation;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendationExplanationRepository extends MongoRepository<RecommendationExplanation, String> {

    Optional<RecommendationExplanation> findByRecommendExplanationId(String recommendExplanationId);

    Optional<RecommendationExplanation> findTopByRecommendationRunIdOrderByGeneratedAtDesc(String recommendationRunId);
}
