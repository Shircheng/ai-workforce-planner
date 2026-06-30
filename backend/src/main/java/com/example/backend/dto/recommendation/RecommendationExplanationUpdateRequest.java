package com.example.backend.dto.recommendation;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationExplanationUpdateRequest {

    private String explanationStatus;
    private String aiExplanation;
    private Instant aiGeneratedAt;
}
