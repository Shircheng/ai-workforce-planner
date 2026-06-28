package com.example.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
@Document(collection = "bench")
public class Bench {

    @Id
    private ObjectId id;
    private String benchRecordId;
    private String employeeId;
    private String employeeName;
    private String benchType;
    private String availabilityCategory;
    private LocalDate availableFrom;
    private BigDecimal benchFTE;
    private BigDecimal benchPercent;
    private String primaryDomain;
    private List<String> topSkills;
    private String benchRisk;
    private Integer timeOnBenchDays;
    private String suggestedAction;
    private String targetRoleFit;
    private String ewaActionRequired;
    private String isAlsoInPartialCapacityView;
    private String recordUsage;
}
