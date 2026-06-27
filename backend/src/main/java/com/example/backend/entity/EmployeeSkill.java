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
public class EmployeeSkill {

    @Id
    private String id;
    private String skillRowId;
    private String employeeId;
    private String employeeName;
    private String skillName;
    private String skillCategory;
    private Integer skillLevel;
    private BigDecimal yearsExperience;
    private String lastUsedDate;
    private String evidenceSource;
    private String confidence;
}
