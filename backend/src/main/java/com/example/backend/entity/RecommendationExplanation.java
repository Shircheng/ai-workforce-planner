package com.example.backend.entity;

import com.example.backend.dto.recommendation.explanation.RecommendationRunExplanation;
import java.time.Instant;
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
@Document(collection = "recommendation_explaination")
public class RecommendationExplanation {

    @Id
    private ObjectId id;
    private String recommendExplanationId;
    private String recommendationRunId;
    private Instant generatedAt;
    private String modelUsed;
    private Boolean fallbackUsed;
    private String error;
    private RecommendationRunExplanation explanations;
    private Instant createdAt;
    private Instant updatedAt;
}
