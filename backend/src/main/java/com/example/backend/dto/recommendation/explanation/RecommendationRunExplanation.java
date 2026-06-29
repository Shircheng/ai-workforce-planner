package com.example.backend.dto.recommendation.explanation;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRunExplanation {
    private String runSummary;
    private List<OptionExplanation> options;
}
