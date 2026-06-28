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
@Document(collection = "employee_skills")
public class EmployeeSkill {

    @Id
    private ObjectId id;
    private String skillRowId;
    private String employeeId;
    private String employeeName;
    private String skillName;
    private String skillCategory;
    private Integer skillLevel;
    private BigDecimal yearsExperience;
    private LocalDate lastUsedDate;
    private String evidenceSource;
    private String confidence;
}
