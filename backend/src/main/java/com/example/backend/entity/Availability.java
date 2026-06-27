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
public class Availability {

    @Id
    private String id;
    private String availabilityId;
    private String employeeId;
    private String employeeName;
    private String weekStartDate;
    private BigDecimal availableFTE;
    private String availabilityType;
    private String source;
    private String confidence;
    private String ewaStatus;
    private String notes;
}
