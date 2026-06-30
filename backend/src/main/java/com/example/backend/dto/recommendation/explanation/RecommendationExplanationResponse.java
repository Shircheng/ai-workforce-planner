package com.example.backend.dto.recommendation.explanation;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationExplanationResponse {

    private String recommendExplanationId;
    private String recommendationRunId;
    private String explanationStatus;
    private Instant generatedAt;
    private String modelUsed;
    private Boolean fallbackUsed;
    private String error;
    private Boolean cached;
    private RecommendationRunExplanation explanations;
}
