package com.example.backend.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.backend.dto.recommendation.explanation.RecommendationRunExplanation;
import com.example.backend.entity.RecommendationExplanation;
import com.example.backend.entity.RecommendationRun;
import com.example.backend.repository.RecommendationExplanationRepository;
import com.example.backend.repository.RecommendationRunRepository;
import com.example.backend.service.ai.OpenAiExplanationClient.OpenAiExplanationResult;
import com.example.backend.service.ai.RecommendationExplanationEvidenceBuilder.MemberEvidence;
import com.example.backend.service.ai.RecommendationExplanationEvidenceBuilder.OptionEvidence;
import com.example.backend.service.ai.RecommendationExplanationEvidenceBuilder.RecommendationExplanationEvidence;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AiRecommendationExplanationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generatesFallbackAndSavesWhenOpenAiApiKeyIsMissing() {
        RecommendationRun run = runWithoutExplanation();
        RecommendationRunRepository repository = mock(RecommendationRunRepository.class);
        RecommendationExplanationRepository explanationRepository = mock(RecommendationExplanationRepository.class);
        RecommendationExplanationEvidenceBuilder builder = mock(RecommendationExplanationEvidenceBuilder.class);
        when(repository.findByRecommendationRunId("RUN-1")).thenReturn(Optional.of(run));
        when(repository.save(any(RecommendationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(explanationRepository.findTopByRecommendationRunIdOrderByGeneratedAtDesc("RUN-1"))
                .thenReturn(Optional.empty());
        when(explanationRepository.save(any(RecommendationExplanation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(builder.build(run)).thenReturn(sampleEvidence());
        OpenAiExplanationClient client = new OpenAiExplanationClient(
                RestClient.builder(),
                "",
                "gpt-test",
                true,
                Duration.ofSeconds(1)
        );
        AiRecommendationExplanationService service = new AiRecommendationExplanationService(
                client,
                builder,
                repository,
                explanationRepository,
                objectMapper
        );

        var response = service.generateExplanations("RUN-1", false);

        assertThat(response.getCached()).isFalse();
        assertThat(response.getFallbackUsed()).isTrue();
        assertThat(response.getExplanationStatus()).isEqualTo("AI_EXPLANATION_GENERATED");
        assertThat(response.getExplanations().getOptions()).hasSize(1);
        assertThat(response.getExplanations().getOptions().getFirst().getMembers()).hasSize(1);
        assertThat(response.getRecommendExplanationId()).startsWith("REC-EXP-");
        assertThat(response.getRecommendationRunId()).isEqualTo("RUN-1");
        assertThat(response.getError()).contains("OPENAI_API_KEY");
        assertThat(run.getAiExplanation()).isNull();
        assertThat(run.getAiGeneratedAt()).isNull();
        ArgumentCaptor<RecommendationExplanation> explanationCaptor = ArgumentCaptor.forClass(RecommendationExplanation.class);
        verify(explanationRepository).save(explanationCaptor.capture());
        assertThat(explanationCaptor.getValue().getRecommendExplanationId()).startsWith("REC-EXP-");
        assertThat(explanationCaptor.getValue().getRecommendationRunId()).isEqualTo("RUN-1");
        assertThat(explanationCaptor.getValue().getExplanations()).isNotNull();
    }

    @Test
    void cachedExplanationsDoNotCallOpenAiAgain() {
        RecommendationRun run = runWithExplanation();
        RecommendationRunRepository repository = mock(RecommendationRunRepository.class);
        RecommendationExplanationRepository explanationRepository = mock(RecommendationExplanationRepository.class);
        RecommendationExplanationEvidenceBuilder builder = mock(RecommendationExplanationEvidenceBuilder.class);
        OpenAiExplanationClient client = mock(OpenAiExplanationClient.class);
        when(repository.findByRecommendationRunId("RUN-1")).thenReturn(Optional.of(run));
        when(explanationRepository.findTopByRecommendationRunIdOrderByGeneratedAtDesc("RUN-1"))
                .thenReturn(Optional.of(explanationDocument()));
        AiRecommendationExplanationService service = new AiRecommendationExplanationService(
                client,
                builder,
                repository,
                explanationRepository,
                objectMapper
        );

        var response = service.generateExplanations("RUN-1", false);

        assertThat(response.getCached()).isTrue();
        assertThat(response.getRecommendExplanationId()).isEqualTo("REC-EXP-CACHED");
        assertThat(response.getExplanations().getRunSummary()).contains("cached");
        verify(client, never()).generateJson(anyString(), anyString());
        verify(builder, never()).build(any());
        verify(explanationRepository, never()).save(any());
    }

    @Test
    void forceRegenerateIgnoresCacheAndSavesOpenAiResult() {
        RecommendationRun run = runWithExplanation();
        RecommendationRunRepository repository = mock(RecommendationRunRepository.class);
        RecommendationExplanationRepository explanationRepository = mock(RecommendationExplanationRepository.class);
        RecommendationExplanationEvidenceBuilder builder = mock(RecommendationExplanationEvidenceBuilder.class);
        OpenAiExplanationClient client = mock(OpenAiExplanationClient.class);
        when(repository.findByRecommendationRunId("RUN-1")).thenReturn(Optional.of(run));
        when(repository.save(any(RecommendationRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(explanationRepository.findTopByRecommendationRunIdOrderByGeneratedAtDesc("RUN-1"))
                .thenReturn(Optional.of(explanationDocument()));
        when(explanationRepository.save(any(RecommendationExplanation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(builder.build(run)).thenReturn(sampleEvidence());
        when(client.generateJson(anyString(), anyString())).thenReturn(new OpenAiExplanationResult("""
                {
                  "runSummary": "AI generated run explanation.",
                  "options": [
                    {
                      "optionId": "RUN-1-OPTION-1",
                      "optionType": "BALANCED",
                      "teamSummary": "The team balances capability and readiness.",
                      "reasoningBullets": ["Selected by backend scoring.", "Covers core Java evidence."],
                      "riskSummary": "Kafka gap and partial availability remain.",
                      "nextActions": ["Mitigate Kafka gap.", "Complete EWA approval."],
                      "ewaSummary": "EWA remains required before booking.",
                      "members": [
                        {
                          "employeeId": "EMP-1",
                          "opportunityRoleId": "ROLE-1",
                          "recommendationNote": "Alex is suitable for the architect role.",
                          "reasoningBullets": ["Strong Java and MongoDB evidence."],
                          "riskSummary": "Kafka is missing.",
                          "nextActions": ["Pair with Kafka specialist."],
                          "ewaSummary": "EWA approval remains pending."
                        }
                      ]
                    }
                  ]
                }
                """, "gpt-ai"));

        AiRecommendationExplanationService service = new AiRecommendationExplanationService(
                client,
                builder,
                repository,
                explanationRepository,
                objectMapper
        );

        var response = service.generateExplanations("RUN-1", true);

        assertThat(response.getCached()).isFalse();
        assertThat(response.getFallbackUsed()).isFalse();
        assertThat(response.getRecommendExplanationId()).isEqualTo("REC-EXP-CACHED");
        assertThat(response.getModelUsed()).isEqualTo("gpt-ai");
        assertThat(response.getExplanations().getRunSummary()).contains("AI generated");
        assertThat(response.getExplanations().getOptions().getFirst().getMembers().getFirst().getRecommendationNote())
                .contains("architect role");
        ArgumentCaptor<RecommendationRun> captor = ArgumentCaptor.forClass(RecommendationRun.class);
        verify(repository, atLeast(2)).save(captor.capture());
        assertThat(captor.getAllValues().getLast().getExplanationStatus()).isEqualTo("AI_EXPLANATION_GENERATED");
        assertThat(captor.getAllValues().getLast().getAiExplanation()).isNull();
        assertThat(captor.getAllValues().getLast().getAiGeneratedAt()).isNull();
        ArgumentCaptor<RecommendationExplanation> explanationCaptor = ArgumentCaptor.forClass(RecommendationExplanation.class);
        verify(explanationRepository).save(explanationCaptor.capture());
        assertThat(explanationCaptor.getValue().getRecommendExplanationId()).isEqualTo("REC-EXP-CACHED");
        assertThat(explanationCaptor.getValue().getRecommendationRunId()).isEqualTo("RUN-1");
        assertThat(explanationCaptor.getValue().getExplanations().getRunSummary()).contains("AI generated");
    }

    @Test
    void promptIsBuiltFromBackendEvidenceAndDoesNotUseFrontendNextActions() {
        AiRecommendationExplanationService service = new AiRecommendationExplanationService(
                mock(OpenAiExplanationClient.class),
                mock(RecommendationExplanationEvidenceBuilder.class),
                mock(RecommendationRunRepository.class),
                mock(RecommendationExplanationRepository.class),
                objectMapper
        );

        String prompt = service.buildUserPrompt(sampleEvidence());

        assertThat(prompt)
                .contains("\"recommendationRunId\":\"RUN-1\"")
                .contains("\"employeeId\":\"EMP-1\"")
                .contains("\"missingRequiredSkills\":[\"Kafka\"]")
                .contains("The frontend did not supply next actions");
        assertThat(prompt)
                .doesNotContain("frontendNextActions")
                .doesNotContain("Salary")
                .doesNotContain("Bill rate");
    }

    @Test
    void recommendationGenerationServiceDoesNotReferenceAiExplanationLayer() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/example/backend/service/RecommendationGenerationService.java"
        ));

        assertThat(source)
                .doesNotContain("AiRecommendationExplanationService")
                .doesNotContain("OpenAiExplanationClient")
                .doesNotContain("RecommendationExplanationController")
                .doesNotContain("/recommendations/explanations")
                .doesNotContain("ai.openai");
    }

    private RecommendationRun runWithoutExplanation() {
        RecommendationRun run = new RecommendationRun();
        run.setRecommendationRunId("RUN-1");
        run.setOpportunityId("OPP-1");
        run.setOpportunityName("CRM modernization");
        run.setExplanationStatus("PENDING_AI_INTEGRATION");
        return run;
    }

    private RecommendationRun runWithExplanation() {
        RecommendationRun run = runWithoutExplanation();
        run.setExplanationStatus("AI_EXPLANATION_GENERATED");
        return run;
    }

    private RecommendationExplanation explanationDocument() {
        return RecommendationExplanation.builder()
                .recommendExplanationId("REC-EXP-CACHED")
                .recommendationRunId("RUN-1")
                .generatedAt(Instant.parse("2026-06-29T00:00:00Z"))
                .modelUsed("gpt-cached")
                .fallbackUsed(false)
                .explanations(RecommendationRunExplanation.builder()
                        .runSummary("cached explanation from recommendation_explaination")
                        .options(List.of())
                        .build())
                .build();
    }

    private RecommendationExplanationEvidence sampleEvidence() {
        MemberEvidence member = new MemberEvidence(
                "EMP-1",
                "Alex Tan",
                "ROLE-1",
                "Solution Architect",
                1,
                "BestFit",
                Map.of(
                        "capabilityFitScore", new BigDecimal("92"),
                        "availabilityFitScore", new BigDecimal("75"),
                        "overallStaffingScore", new BigDecimal("88"),
                        "rank", 1
                ),
                Map.of(
                        "roleName", "Solution Architect",
                        "gradePreference", "M",
                        "requiredSkills", List.of("Java", "MongoDB", "Kafka"),
                        "desiredSkills", List.of("Spring Boot"),
                        "startDate", "2026-07-01",
                        "fteRequired", new BigDecimal("1.0"),
                        "canCombineCandidates", "Yes"
                ),
                Map.of(
                        "employeeName", "Alex Tan",
                        "grade", "M",
                        "city", "Kuala Lumpur",
                        "country", "Malaysia",
                        "region", "MY",
                        "primaryDomain", "CRM"
                ),
                List.of("Java", "MongoDB"),
                List.of("Kafka"),
                List.of("Spring Boot"),
                List.of(),
                List.of(Map.of("skillName", "Java", "skillLevel", 5)),
                Map.of("profileSummary", "Integration architect"),
                List.of(Map.of("projectName", "CRM integration", "domain", "CRM")),
                List.of(),
                List.of(Map.of("weekStartDate", "2026-07-01", "availableFTE", new BigDecimal("0.5"))),
                List.of(),
                Map.of("rationale", "2/3 required skills evidenced"),
                List.of(Map.of("ewaStatus", "Pending", "blockingReason", "Needs approval")),
                new BigDecimal("0.5"),
                new BigDecimal("1.0"),
                new BigDecimal("0.5"),
                "2026-08-01",
                "Pending",
                "Needs approval",
                "Yes",
                "2/3 required skills evidenced; 0.5/1.0 FTE available.",
                "Insufficient availability at start"
        );
        OptionEvidence option = new OptionEvidence(
                "RUN-1-OPTION-1",
                "BALANCED",
                "Balanced",
                new BigDecimal("82"),
                new BigDecimal("25"),
                "MEDIUM",
                14,
                1,
                List.of("availability risk"),
                List.of("Kafka"),
                List.of(member)
        );
        return new RecommendationExplanationEvidence(
                "RUN-1",
                "OPP-1",
                "CRM modernization",
                "2026-06-29T00:00:00Z",
                Map.of("domain", "CRM", "expectedStartDate", "2026-07-01"),
                List.of(option)
        );
    }
}
