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
public class Bench {

    @Id
    private String id;
    private String benchRecordId;
    private String employeeId;
    private String employeeName;
    private String benchType;
    private String availabilityCategory;
    private String availableFrom;
    private BigDecimal benchFTE;
    private BigDecimal benchPercent;
    private String primaryDomain;
    private String topSkills;
    private String benchRisk;
    private Integer timeOnBenchDays;
    private String suggestedAction;
    private String targetRoleFit;
    private String ewaActionRequired;
    private String isAlsoInPartialCapacityView;
    private String recordUsage;
}
