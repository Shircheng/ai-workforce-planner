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
public class Allocation {

    @Id
    private String id;
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
    private String startDate;
    private String plannedEndDate;
    private String allocationStatus;
    private String ewaStatus;
    private String lastUpdated;
}
