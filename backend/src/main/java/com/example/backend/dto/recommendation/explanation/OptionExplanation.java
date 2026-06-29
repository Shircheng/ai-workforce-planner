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
public class OptionExplanation {

    private String optionId;
    private String optionType;
    private String optionName;
    private String teamSummary;
    private List<String> reasoningBullets;
    private String riskSummary;
    private List<String> nextActions;
    private String ewaSummary;
    private List<MemberExplanation> members;
}
