package com.example.backend.entity;

import java.time.Instant;
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
@Document(collection = "recommendation_runs")
public class RecommendationRun {

    @Id
    private ObjectId id;
    private String recommendationRunId;
    private String opportunityId;
    private String opportunityName;
    private Instant generatedAt;
    private Integer overlayCount;
    private String explanationStatus;
    private String aiExplanation;
    private Instant aiGeneratedAt;
    private Instant updatedAt;
    private List<RecommendationRunOption> options;
}
