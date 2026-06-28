package com.example.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
@Document(collection = "opportunity_roles")
public class OpportunityRole {

    @Id
    private ObjectId id;
    private String opportunityRoleId;
    private String opportunityId;
    private String roleName;
    private String disciplineOrDepartment;
    private String gradePreference;
    private List<String> requiredSkills;
    private List<String> desiredSkills;
    private String domainExperienceRequired;
    private String locationPreference;
    private LocalDate startDate;
    private Integer durationWeeks;
    private BigDecimal fteRequired;
    private String priority;
    private String flexibilityNotes;
    private BigDecimal minimumIndividualFTE;
    private String canCombineCandidates;
}
