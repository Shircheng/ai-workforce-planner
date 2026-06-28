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
@Document(collection = "ewa_requests")
public class EwaRequest {

    @Id
    private ObjectId id;
    private String ewaRequestId;
    private String opportunityId;
    private String opportunityRoleId;
    private String employeeId;
    private String employeeName;
    private String requestType;
    private String ewaStatus;
    private BigDecimal requestedFTE;
    private LocalDate proposedStartDate;
    private LocalDate proposedEndDate;
    private String approvalRequired;
    private String bookingOwner;
    private String blockingReason;
    private String nextAction;
    private LocalDate lastUpdated;
    private String notes;
    private BigDecimal availableFTEAtStart;
    private BigDecimal fteGap;
    private String canSplitRole;
    private LocalDate earliestFullAvailabilityDate;
}
