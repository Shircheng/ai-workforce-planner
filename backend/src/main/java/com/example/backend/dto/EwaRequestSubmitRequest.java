package com.example.backend.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class EwaRequestSubmitRequest {

    private String opportunityId;
    private String opportunityRoleId;
    private String employeeId;
    private BigDecimal availableFTEAtStart;
    private BigDecimal fteGap;
    private String notes;
}
