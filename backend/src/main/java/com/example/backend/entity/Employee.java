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
public class Employee {

    @Id
    private String id;
    private String employeeId;
    private String employeeName;
    private String region;
    private String country;
    private String city;
    private String timezone;
    private String department;
    private String discipline;
    private String roleArchetype;
    private String grade;
    private Integer careerLevel;
    private String primaryDomain;
    private String secondaryDomain;
    private String availabilityCategory;
    private BigDecimal currentAllocationFTE;
    private BigDecimal availableFTECurrent;
    private String expectedReleaseDate;
    private String releaseWindow;
    private String ewaStatus;
    private String currentAccountId;
    private String currentProjectId;
    private String currentRole;
    private String currentProjectStart;
    private String currentProjectEnd;
    private String workMode;
    private String profileId;
}
