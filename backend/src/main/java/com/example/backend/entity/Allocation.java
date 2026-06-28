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
@Document(collection = "allocations")
public class Allocation {

    @Id
    private ObjectId id;
    private String allocationId;
    private String employeeId;
    private String employeeName;
    private String accountId;
    private String clientName;
    private String clientType;
    private String projectId;
    private String projectName;
    private String domain;
    private String roleOnProject;
    private BigDecimal allocationFTE;
    private LocalDate startDate;
    private LocalDate plannedEndDate;
    private String allocationStatus;
    private String ewaStatus;
    private LocalDate lastUpdated;
}
