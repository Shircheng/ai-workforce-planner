package com.example.backend.controller;

import com.example.backend.dto.recommendation.RecommendationExplanationUpdateRequest;
import com.example.backend.dto.recommendation.RecommendationGenerateResponse;
import com.example.backend.entity.RecommendationRun;
import com.example.backend.service.RecommendationGenerationService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecommendationRunController {

    private final RecommendationGenerationService recommendationGenerationService;

    public RecommendationRunController(RecommendationGenerationService recommendationGenerationService) {
        this.recommendationGenerationService = recommendationGenerationService;
    }

    @PostMapping("/opportunities/{opportunityId}/recommendations/generate")
    public RecommendationGenerateResponse generateRecommendations(@PathVariable String opportunityId) {
        return recommendationGenerationService.generateRecommendations(opportunityId);
    }

    @GetMapping("/recommendation-runs/{recommendationRunId}")
    public RecommendationRun getRecommendationRun(@PathVariable String recommendationRunId) {
        return recommendationGenerationService.getRecommendationRun(recommendationRunId);
    }

    @GetMapping("/opportunities/{opportunityId}/recommendations/latest")
    public RecommendationRun getLatestRecommendationRunForOpportunity(@PathVariable String opportunityId) {
        return recommendationGenerationService.getLatestRecommendationRunForOpportunity(opportunityId);
    }

    @PatchMapping("/recommendation-runs/{recommendationRunId}/explanation")
    public RecommendationRun updateRecommendationExplanation(
            @PathVariable String recommendationRunId,
            @RequestBody RecommendationExplanationUpdateRequest request
    ) {
        return recommendationGenerationService.updateRecommendationExplanation(recommendationRunId, request);
    }
}
