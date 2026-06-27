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
public class ProjectHistory {

    @Id
    private String id;
    private String historyId;
    private String employeeId;
    private String employeeName;
    private String clientName;
    private String clientType;
    private String projectName;
    private String domain;
    private String role;
    private String startDate;
    private String endDate;
    private String keyTechnologiesOrMethods;
    private String responsibilities;
    private String outcomeEvidence;
    private String region;
    private Integer teamSize;
}
