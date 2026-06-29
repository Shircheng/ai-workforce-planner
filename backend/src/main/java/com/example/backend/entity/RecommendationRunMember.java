package com.example.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRunMember {

    private String opportunityRoleId;
    private String roleName;
    private String employeeId;
    private String employeeName;
    private Integer rank;
    private String fitStatus;
    private BigDecimal matchScore;
    private BigDecimal capabilityFitScore;
    private BigDecimal availabilityFitScore;
    private BigDecimal overallStaffingScore;
    private BigDecimal availableFteAtStart;
    private BigDecimal fteGap;
    private LocalDate earliestFullAvailabilityDate;
    private String rationale;
    private String constraint;
}
