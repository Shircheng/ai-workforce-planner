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
@Document(collection = "availability")
public class Availability {

    @Id
    private ObjectId id;
    private String availabilityId;
    private String employeeId;
    private String employeeName;
    private LocalDate weekStartDate;
    private BigDecimal availableFTE;
    private String availabilityType;
    private String source;
    private String confidence;
    private String ewaStatus;
    private String notes;
}
