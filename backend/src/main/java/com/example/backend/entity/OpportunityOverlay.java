package com.example.backend.entity;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityOverlay {

    @Id
    private String id;
    private String overlayId;
    private String opportunityId;
    private String opportunityRoleId;
    private String employeeId;
    private String employeeName;
    private String fitStatus;
    private Integer rank;
    private BigDecimal matchScore;
    private String rationale;
    private String constraint;
    private String ewaStatus;
    private String plannerNotes;
    private BigDecimal capabilityFitScore;
    private BigDecimal availabilityFitScore;
    private BigDecimal overallStaffingScore;
    private BigDecimal availableFTEAtStart;
    private BigDecimal fteGap;
    private String earliestFullAvailabilityDate;
    private Integer requiredSkillsMatched;
    private Integer requiredSkillsTotal;
    private Integer desiredSkillsMatched;
    private Integer desiredSkillsTotal;
}
