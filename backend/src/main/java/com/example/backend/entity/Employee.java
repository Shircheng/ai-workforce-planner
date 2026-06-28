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
@Document(collection = "employees")
public class Employee {

    @Id
    private ObjectId id;
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
    private LocalDate expectedReleaseDate;
    private String releaseWindow;
    private String ewaStatus;
    private String currentAccountId;
    private String currentProjectId;
    private String currentRole;
    private LocalDate currentProjectStart;
    private LocalDate currentProjectEnd;
    private String workMode;
    private String profileId;
}
