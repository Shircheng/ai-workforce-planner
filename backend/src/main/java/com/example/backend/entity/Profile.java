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
public class Profile {

    @Id
    private String id;
    private String profileId;
    private String employeeId;
    private String employeeName;
    private String profileSummary;
    private String keyStrengths;
    private String preferredWorkTypes;
    private String domainExperienceSummary;
    private String certifications;
    private String recentHighlights;
    private String mobilityNotes;
    private String languages;
}
