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
public class Opportunity {

    @Id
    private String id;
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
    private String expectedStartDate;
    private Integer durationWeeks;
    private String commercialPriority;
    private String deliveryRisk;
    private String opportunityBrief;
    private String timezonePreference;
}
