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
public class OpportunityRole {

    @Id
    private String id;
    private String opportunityRoleId;
    private String opportunityId;
    private String roleName;
    private String disciplineOrDepartment;
    private String gradePreference;
    private String requiredSkills;
    private String desiredSkills;
    private String domainExperienceRequired;
    private String locationPreference;
    private String startDate;
    private Integer durationWeeks;
    private BigDecimal fteRequired;
    private String priority;
    private String flexibilityNotes;
    private BigDecimal minimumIndividualFTE;
    private String canCombineCandidates;
}
