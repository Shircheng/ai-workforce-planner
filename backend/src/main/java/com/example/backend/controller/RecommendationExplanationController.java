package com.example.backend.controller;

import com.example.backend.dto.recommendation.explanation.RecommendationExplanationResponse;
import com.example.backend.service.ai.AiRecommendationExplanationService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RecommendationExplanationController {

  private final AiRecommendationExplanationService explanationService;

  public RecommendationExplanationController(
      AiRecommendationExplanationService explanationService) {
    this.explanationService = explanationService;
  }

  @PostMapping({"/recommendation-runs/{recommendationRunId}/explanations/generate"})
  public RecommendationExplanationResponse generateRecommendationExplanations(
      @PathVariable String recommendationRunId,
      @RequestParam(defaultValue = "false") boolean forceRegenerate) {
    return explanationService.generateExplanations(recommendationRunId, forceRegenerate);
  }

  @GetMapping({"/recommendation-runs/{recommendationRunId}/explanations"})
  public RecommendationExplanationResponse getRecommendationExplanations(
      @PathVariable String recommendationRunId) {
    return explanationService.getExplanations(recommendationRunId);
  }
}
