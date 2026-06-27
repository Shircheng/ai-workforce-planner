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
public class EwaRequest {

    @Id
    private String id;
    private String ewaRequestId;
    private String opportunityId;
    private String opportunityRoleId;
    private String employeeId;
    private String employeeName;
    private String requestType;
    private String ewaStatus;
    private BigDecimal requestedFTE;
    private String proposedStartDate;
    private String proposedEndDate;
    private String approvalRequired;
    private String bookingOwner;
    private String blockingReason;
    private String nextAction;
    private String lastUpdated;
    private String notes;
    private BigDecimal availableFTEAtStart;
    private BigDecimal fteGap;
    private String canSplitRole;
    private String earliestFullAvailabilityDate;
}
