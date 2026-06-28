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
@Document(collection = "profiles")
public class Profile {

    @Id
    private ObjectId id;
    private String profileId;
    private String employeeId;
    private String employeeName;
    private String profileSummary;
    private List<String> keyStrengths;
    private List<String> preferredWorkTypes;
    private Map<String, String> domainExperienceSummary;
    private List<String> certifications;
    private List<String> recentHighlights;
    private String mobilityNotes;
    private List<String> languages;
}
