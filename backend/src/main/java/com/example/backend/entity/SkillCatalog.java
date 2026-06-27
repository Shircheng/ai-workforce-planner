package com.example.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillCatalog {

    @Id
    private String id;
    private String skillName;
    private String skillCategory;
    private String description;
    private String relevantDepartments;
    private String suggestedLevelScale;
}
