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
@Document(collection = "opportunities")
public class Opportunity {

    @Id
    private ObjectId id;
    private String opportunityId;
    private String opportunityName;
    private String clientName;
    private String clientType;
    private String region;
    private String country;
    private String city;
    private String domain;
    private String stage;
    private BigDecimal probability;
    private LocalDate expectedStartDate;
    private Integer durationWeeks;
    private String commercialPriority;
    private String deliveryRisk;
    private String opportunityBrief;
    private String timezonePreference;
}
