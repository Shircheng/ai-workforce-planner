package com.example.backend.entity;

import java.util.List;
import java.util.Map;
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
@Document(collection = "skill_catalog")
public class SkillCatalog {

    @Id
    private ObjectId id;
    private String skillName;
    private String skillCategory;
    private String description;
    private List<String> relevantDepartments;
    private Map<Integer, String> suggestedLevelScale;
}
