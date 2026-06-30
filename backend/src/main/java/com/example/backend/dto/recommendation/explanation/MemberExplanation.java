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
public class MemberExplanation {

    private String employeeId;
    private String employeeName;
    private String opportunityRoleId;
    private String roleName;
    private String recommendationNote;
    private List<String> reasoningBullets;
    private String riskSummary;
    private List<String> nextActions;
}
