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
    private List<String> risks;
    private List<RecommendationOptionMemberDto> members;
}
