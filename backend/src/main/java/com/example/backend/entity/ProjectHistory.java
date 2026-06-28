package com.example.backend.entity;

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
@Document(collection = "project_history")
public class ProjectHistory {

    @Id
    private ObjectId id;
    private String historyId;
    private String employeeId;
    private String employeeName;
    private String clientName;
    private String clientType;
    private String projectName;
    private String domain;
    private String role;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> keyTechnologiesOrMethods;
    private List<String> responsibilities;
    private List<String> outcomeEvidence;
    private String region;
    private Integer teamSize;
}
