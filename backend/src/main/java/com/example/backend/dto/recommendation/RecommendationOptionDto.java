package com.example.backend.dto.recommendation;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationOptionDto {

    private String optionType;
    private BigDecimal confidenceScore;
    private BigDecimal riskScore;
    private String riskLevel;
    private Integer readinessDays;
    private Integer selectedMemberCount;
    private BigDecimal locationFitScore;
    private List<String> locationFit;
    private BigDecimal skillCoverageScore;
    private List<String> matchedRequiredSkills;
    private List<String> missingRequiredSkills;
    private List<String> matchedDesiredSkills;
    private List<String> missingDesiredSkills;
    private List<String> risks;
    private List<RecommendationOptionMemberDto> members;
}
