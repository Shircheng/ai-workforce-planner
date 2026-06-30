package com.example.backend.dto.recommendation;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationGenerateResponse {

    private String recommendationRunId;
    private String opportunityId;
    private String opportunityName;
    private Instant generatedAt;
    private Integer overlayCount;
    private String explanationStatus;
    private List<RecommendationOptionDto> options;
}
