package com.example.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "opportunity_overlays")
public class OpportunityOverlay {

    @Id
    private ObjectId id;
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
    private LocalDate earliestFullAvailabilityDate;
    private Integer requiredSkillsMatched;
    private Integer requiredSkillsTotal;
    private Integer desiredSkillsMatched;
    private Integer desiredSkillsTotal;
}
