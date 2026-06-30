package com.example.backend.dto.opportunity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpportunityRequest {

    @NotBlank(message = "Opportunity statement must not be empty")
    private String statement;

    @NotBlank(message = "Opportunity brief must not be empty")
    private String opportunityBrief;

    @NotBlank(message = "Opportunity name must not be empty")
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9 ]*$", message = "Opportunity name must be alphanumeric")
    private String opportunityName;

    @NotBlank(message = "Client name must not be empty")
    private String clientName;

    private String clientType;

    @NotBlank(message = "Domain must not be empty")
    private String domain;

    private String region;

    @NotBlank(message = "Country must not be empty")
    private String country;

    private String city;

    @NotNull(message = "Probability is required")
    @DecimalMin(value = "0.0", message = "Probability must be at least 0")
    @DecimalMax(value = "1.0", message = "Probability must be at most 1")
    private BigDecimal probability;

    @NotNull(message = "Expected start date is required")
    private LocalDate expectedStartDate;

    @NotNull(message = "Duration weeks is required")
    @Positive(message = "Duration weeks must be greater than zero")
    private Integer durationWeeks;

    @NotBlank(message = "Commercial priority must not be empty")
    private String commercialPriority;

    private String timezonePreference;
}