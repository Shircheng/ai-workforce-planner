package com.example.backend.service.ai;

import com.example.backend.dto.recommendation.explanation.MemberExplanation;
import com.example.backend.dto.recommendation.explanation.OptionExplanation;
import com.example.backend.dto.recommendation.explanation.RecommendationExplanationResponse;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiRecommendationExplanationService {

    static final String SYSTEM_PROMPT = """
            You are a workforce planning assistant. Explain staffing recommendations using only supplied structured evidence. Do not invent employee skills, project history, availability, FTE, scores, EWA status, or risks. Do not change ranking or recommendation scores. Human planners make the final decision. EWA remains the final approval and booking process. Return valid JSON only.
            """;

    private static final String PENDING_AI_INTEGRATION = "PENDING_AI_INTEGRATION";
    private static final String FALLBACK_MODEL = "deterministic-fallback";
    private static final String NOT_SUPPLIED = "not supplied";

    private final OpenAiExplanationClient openAiClient;
    private final RecommendationExplanationEvidenceBuilder evidenceBuilder;
    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendationExplanationRepository recommendationExplanationRepository;
    private final ObjectMapper objectMapper;

    public AiRecommendationExplanationService(
            OpenAiExplanationClient openAiClient,
            RecommendationExplanationEvidenceBuilder evidenceBuilder,
            RecommendationRunRepository recommendationRunRepository,
            RecommendationExplanationRepository recommendationExplanationRepository,
            ObjectMapper objectMapper
    ) {
        this.openAiClient = openAiClient;
        this.evidenceBuilder = evidenceBuilder;
        this.recommendationRunRepository = recommendationRunRepository;
        this.recommendationExplanationRepository = recommendationExplanationRepository;
        this.objectMapper = objectMapper;
    }

    public RecommendationExplanationResponse generateExplanations(String recommendationRunId, boolean forceRegenerate) {
        RecommendationRun run = getRun(recommendationRunId);
        if (!forceRegenerate) {
            Optional<RecommendationExplanation> cachedExplanation = cachedExplanation(run);
            if (cachedExplanation.isPresent()) {
                return toResponse(run, cachedExplanation.get(), true);
            }
        }

        markGenerating(run);
        RecommendationExplanationEvidence evidence = evidenceBuilder.build(run);
        RecommendationRunExplanation explanation;
        String modelUsed = configuredModel();
        boolean fallbackUsed = false;
        String error = null;

        try {
            OpenAiExplanationResult result = openAiClient.generateJson(SYSTEM_PROMPT, buildUserPrompt(evidence));
            modelUsed = result.modelUsed();
            explanation = parseOpenAiExplanation(result.content(), evidence);
        } catch (RuntimeException ex) {
            fallbackUsed = true;
            error = cleanError(ex);
            explanation = fallbackExplanation(evidence);
        }

        Instant generatedAt = Instant.now();
        run.setExplanationStatus(ExplanationStatus.AI_EXPLANATION_GENERATED.name());
        run.setUpdatedAt(generatedAt);

        RecommendationRun savedRun = recommendationRunRepository.save(run);
        RecommendationExplanation savedExplanation = saveExplanationDocument(
                savedRun,
                explanation,
                modelUsed,
                fallbackUsed,
                error,
                generatedAt
        );
        return toResponse(savedRun, savedExplanation, false);
    }

    public RecommendationExplanationResponse getExplanations(String recommendationRunId) {
        RecommendationRun run = getRun(recommendationRunId);
        return cachedExplanation(run)
                .map(explanation -> toResponse(run, explanation, true))
                .orElseGet(() -> toResponse(run, null, false));
    }

    String buildUserPrompt(RecommendationExplanationEvidence evidence) {
        try {
            return """
                    Generate explanations for this full recommendation run.

                    Use the supplied structured evidence only. The backend scoring engine has already selected and ranked options and members. Do not change selected employees, option order, ranks, scores, or staffing logic.

                    Generate nextActions from factual evidence such as missing skills, FTE gap, unavailable start date, EWA status, blocking reason, canSplitRole, location mismatch, grade mismatch, project/domain evidence, and run risks. The frontend did not supply next actions.

                    Recommendation evidence JSON:
                    %s

                    Return JSON:
                    {
                      "runSummary": "string",
                      "options": [
                        {
                          "optionId": "string",
                          "optionType": "string",
                          "teamSummary": "string",
                          "reasoningBullets": ["string"],
                          "riskSummary": "string",
                          "nextActions": ["string"],
                          "ewaSummary": "string",
                          "members": [
                            {
                              "employeeId": "string",
                              "opportunityRoleId": "string",
                              "recommendationNote": "string",
                              "reasoningBullets": ["string"],
                              "riskSummary": "string",
                              "nextActions": ["string"],
                              "ewaSummary": "string"
                            }
                          ]
                        }
                      ]
                    }
                    """.formatted(objectMapper.writeValueAsString(evidence));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize recommendation explanation evidence.", ex);
        }
    }

    private void markGenerating(RecommendationRun run) {
        run.setExplanationStatus(ExplanationStatus.AI_EXPLANATION_GENERATING.name());
        run.setAiExplanation(null);
        run.setAiGeneratedAt(null);
        run.setUpdatedAt(Instant.now());
        recommendationRunRepository.save(run);
    }

    private Optional<RecommendationExplanation> cachedExplanation(RecommendationRun run) {
        return recommendationExplanationRepository.findTopByRecommendationRunIdOrderByGeneratedAtDesc(
                run.getRecommendationRunId()
        );
    }

    private RecommendationRun getRun(String recommendationRunId) {
        return recommendationRunRepository.findByRecommendationRunId(recommendationRunId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recommendation run not found: " + recommendationRunId
                ));
    }

    private RecommendationExplanationResponse toResponse(
            RecommendationRun run,
            RecommendationExplanation explanationDocument,
            boolean cached
    ) {
        return RecommendationExplanationResponse.builder()
                .recommendExplanationId(explanationDocument == null ? null : explanationDocument.getRecommendExplanationId())
                .recommendationRunId(run.getRecommendationRunId())
                .explanationStatus(normalizedStatus(run.getExplanationStatus()))
                .generatedAt(explanationDocument == null ? null : explanationDocument.getGeneratedAt())
                .modelUsed(explanationDocument == null ? null : explanationDocument.getModelUsed())
                .fallbackUsed(explanationDocument != null && Boolean.TRUE.equals(explanationDocument.getFallbackUsed()))
                .error(explanationDocument == null ? null : explanationDocument.getError())
                .cached(cached)
                .explanations(explanationDocument == null ? null : explanationDocument.getExplanations())
                .build();
    }

    private RecommendationExplanation saveExplanationDocument(
            RecommendationRun run,
            RecommendationRunExplanation explanation,
            String modelUsed,
            boolean fallbackUsed,
            String error,
            Instant generatedAt
    ) {
        Instant now = Instant.now();
        RecommendationExplanation document = recommendationExplanationRepository
                .findTopByRecommendationRunIdOrderByGeneratedAtDesc(run.getRecommendationRunId())
                .orElseGet(() -> RecommendationExplanation.builder()
                        .recommendExplanationId(newRecommendExplanationId())
                        .recommendationRunId(run.getRecommendationRunId())
                        .createdAt(now)
                        .build());
        document.setGeneratedAt(generatedAt);
        document.setModelUsed(modelUsed);
        document.setFallbackUsed(fallbackUsed);
        document.setError(error);
        document.setExplanations(explanation);
        document.setUpdatedAt(now);
        return recommendationExplanationRepository.save(document);
    }

    private String newRecommendExplanationId() {
        return "REC-EXP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String normalizedStatus(String status) {
        if (!StringUtils.hasText(status) || PENDING_AI_INTEGRATION.equals(status)) {
            return ExplanationStatus.AI_EXPLANATION_NOT_GENERATED.name();
        }
        return status;
    }

    private RecommendationRunExplanation parseOpenAiExplanation(
            String content,
            RecommendationExplanationEvidence evidence
    ) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            if (!StringUtils.hasText(text(root, "runSummary")) || !root.path("options").isArray()) {
                throw new IllegalArgumentException("OpenAI response did not include runSummary and options.");
            }

            List<JsonNode> optionNodes = jsonArray(root.path("options"));
            if (optionNodes.isEmpty() && !evidence.options().isEmpty()) {
                throw new IllegalArgumentException("OpenAI response did not include option explanations.");
            }

            List<OptionExplanation> options = new ArrayList<>();
            for (int index = 0; index < evidence.options().size(); index++) {
                OptionEvidence option = evidence.options().get(index);
                JsonNode optionNode = findOptionNode(optionNodes, option, index);
                if (optionNode == null) {
                    throw new IllegalArgumentException("OpenAI response missed option " + option.optionId());
                }
                options.add(parseOption(optionNode, option));
            }

            return RecommendationRunExplanation.builder()
                    .runSummary(text(root, "runSummary"))
                    .options(options)
                    .build();
        } catch (Exception ex) {
            throw new IllegalArgumentException("OpenAI response was not valid recommendation explanation JSON.", ex);
        }
    }

    private OptionExplanation parseOption(JsonNode optionNode, OptionEvidence option) {
        List<JsonNode> memberNodes = jsonArray(optionNode.path("members"));
        List<MemberExplanation> members = new ArrayList<>();
        for (int index = 0; index < option.members().size(); index++) {
            MemberEvidence member = option.members().get(index);
            JsonNode memberNode = findMemberNode(memberNodes, member, index);
            if (memberNode == null) {
                throw new IllegalArgumentException("OpenAI response missed member " + member.employeeId());
            }
            members.add(parseMember(memberNode, member));
        }

        return OptionExplanation.builder()
                .optionId(firstNonBlank(text(optionNode, "optionId"), option.optionId()))
                .optionType(firstNonBlank(text(optionNode, "optionType"), option.optionType()))
                .optionName(firstNonBlank(text(optionNode, "optionName"), option.optionName()))
                .teamSummary(requiredText(optionNode, "teamSummary", option.optionId()))
                .reasoningBullets(requiredList(optionNode, "reasoningBullets", option.optionId()))
                .riskSummary(requiredText(optionNode, "riskSummary", option.optionId()))
                .nextActions(requiredList(optionNode, "nextActions", option.optionId()))
                .ewaSummary(requiredText(optionNode, "ewaSummary", option.optionId()))
                .members(members)
                .build();
    }

    private MemberExplanation parseMember(JsonNode memberNode, MemberEvidence member) {
        return MemberExplanation.builder()
                .employeeId(firstNonBlank(text(memberNode, "employeeId"), member.employeeId()))
                .employeeName(member.employeeName())
                .opportunityRoleId(firstNonBlank(text(memberNode, "opportunityRoleId"), member.opportunityRoleId()))
                .roleName(member.roleName())
                .recommendationNote(requiredText(memberNode, "recommendationNote", member.employeeId()))
                .reasoningBullets(requiredList(memberNode, "reasoningBullets", member.employeeId()))
                .riskSummary(requiredText(memberNode, "riskSummary", member.employeeId()))
                .nextActions(requiredList(memberNode, "nextActions", member.employeeId()))
                .ewaSummary(requiredText(memberNode, "ewaSummary", member.employeeId()))
                .build();
    }

    private RecommendationRunExplanation fallbackExplanation(RecommendationExplanationEvidence evidence) {
        List<OptionExplanation> options = evidence.options().stream()
                .map(this::fallbackOption)
                .toList();

        return RecommendationRunExplanation.builder()
                .runSummary("Recommendation run " + evidence.recommendationRunId()
                        + " for " + value(evidence.opportunityName())
                        + " includes " + evidence.options().size()
                        + " option(s). Explanations use backend scoring output and related workforce records. Human planners make the final decision, and EWA remains the final approval and booking process.")
                .options(options)
                .build();
    }

    private OptionExplanation fallbackOption(OptionEvidence option) {
        List<MemberExplanation> members = option.members().stream()
                .map(this::fallbackMember)
                .toList();

        return OptionExplanation.builder()
                .optionId(option.optionId())
                .optionType(option.optionType())
                .optionName(option.optionName())
                .teamSummary(option.optionName() + " is a backend-generated recommendation option with confidence score "
                        + value(option.confidenceScore()) + ", risk level " + value(option.riskLevel())
                        + ", and " + option.members().size() + " recommended member(s).")
                .reasoningBullets(List.of(
                        "The backend scoring engine selected the option and member assignments; OpenAI did not change ranking, staffing, or scores.",
                        "Option risks from the run: " + value(option.risks()) + ".",
                        "Missing required skills across the option: " + value(option.missingSkills()) + ".",
                        "Readiness evidence: " + value(option.readinessDays()) + " readiness day(s), risk score " + value(option.riskScore()) + "."
                ))
                .riskSummary("Key risks/gaps: " + value(option.risks())
                        + ". Missing skills: " + value(option.missingSkills()) + ".")
                .nextActions(optionNextActions(option, members))
                .ewaSummary("EWA remains the final approval and booking process for every recommended team member. Member EWA statuses: "
                        + value(option.members().stream().map(MemberEvidence::ewaStatus).filter(StringUtils::hasText).distinct().toList()) + ".")
                .members(members)
                .build();
    }

    private MemberExplanation fallbackMember(MemberEvidence member) {
        String overallScore = value(member.scores().get("overallStaffingScore"));
        String capabilityScore = value(member.scores().get("capabilityFitScore"));
        String availabilityScore = value(member.scores().get("availabilityFitScore"));

        return MemberExplanation.builder()
                .employeeId(member.employeeId())
                .employeeName(member.employeeName())
                .opportunityRoleId(member.opportunityRoleId())
                .roleName(member.roleName())
                .recommendationNote(value(member.employeeName()) + " is recommended for "
                        + value(member.roleName()) + " with overall staffing score " + overallScore
                        + ". The recommendation is based on stored backend scoring and related workforce evidence.")
                .reasoningBullets(List.of(
                        "Score evidence: capability " + capabilityScore + ", availability " + availabilityScore
                                + ", overall " + overallScore + ", rank " + value(member.rank()) + ".",
                        "Skill evidence: matched required skills " + value(member.matchedRequiredSkills())
                                + "; missing required skills " + value(member.missingRequiredSkills()) + ".",
                        "Availability evidence: " + value(member.availableFteAtStart()) + " FTE available at start against "
                                + value(member.fteRequired()) + " FTE required; FTE gap " + value(member.fteGap()) + ".",
                        "Domain/project evidence: " + domainProjectEvidence(member) + "."
                ))
                .riskSummary(memberRiskSummary(member))
                .nextActions(memberNextActions(member))
                .ewaSummary("EWA status: " + value(member.ewaStatus())
                        + ". EWA remains the final approval and booking step before the employee is booked.")
                .build();
    }

    private List<String> optionNextActions(OptionEvidence option, List<MemberExplanation> members) {
        List<String> actions = new ArrayList<>();
        if (!option.missingSkills().isEmpty()) {
            actions.add("Confirm coverage or mitigation for missing skills: " + value(option.missingSkills()) + ".");
        }
        if (!option.risks().isEmpty()) {
            actions.add("Review option risks before planner approval: " + value(option.risks()) + ".");
        }
        members.stream()
                .flatMap(member -> safeList(member.getNextActions()).stream())
                .filter(StringUtils::hasText)
                .limit(4)
                .forEach(actions::add);
        if (actions.isEmpty()) {
            actions.add("Review the backend recommendation evidence and complete EWA approval before booking.");
        }
        return distinct(actions);
    }

    private List<String> memberNextActions(MemberEvidence member) {
        List<String> actions = new ArrayList<>();
        if (!member.missingRequiredSkills().isEmpty()) {
            actions.add("Confirm mitigation for missing required skills: " + value(member.missingRequiredSkills()) + ".");
        }
        if (!member.missingDesiredSkills().isEmpty()) {
            actions.add("Decide whether missing desired skills need support: " + value(member.missingDesiredSkills()) + ".");
        }
        if (isPositive(member.fteGap())) {
            if (isTruthy(member.canSplitRole())) {
                actions.add("Resolve FTE gap of " + value(member.fteGap()) + " by confirming split-role coverage or phased booking.");
            } else {
                actions.add("Resolve FTE gap of " + value(member.fteGap()) + " before booking.");
            }
        }
        if (StringUtils.hasText(member.earliestFullAvailabilityDate())) {
            String roleStart = textMap(member.role(), "startDate");
            if (StringUtils.hasText(roleStart) && member.earliestFullAvailabilityDate().compareTo(roleStart) > 0) {
                actions.add("Confirm coverage until earliest full availability date " + member.earliestFullAvailabilityDate() + ".");
            }
        }
        if (StringUtils.hasText(member.blockingReason())) {
            actions.add("Resolve blocking reason: " + member.blockingReason() + ".");
        }
        String employeeGrade = textMap(member.employee(), "grade");
        String gradePreference = textMap(member.role(), "gradePreference");
        if (StringUtils.hasText(employeeGrade)
                && StringUtils.hasText(gradePreference)
                && !normalize(employeeGrade).equals(normalize(gradePreference))) {
            actions.add("Confirm grade fit between employee grade " + employeeGrade + " and role preference " + gradePreference + ".");
        }
        String locationPreference = textMap(member.role(), "locationPreference");
        String employeeLocation = value(List.of(
                textMap(member.employee(), "city"),
                textMap(member.employee(), "country"),
                textMap(member.employee(), "region")
        ));
        if (StringUtils.hasText(locationPreference)
                && !isFlexibleLocation(locationPreference)
                && !normalize(employeeLocation).contains(normalize(locationPreference))) {
            actions.add("Confirm location fit for role preference " + locationPreference + " against employee location " + employeeLocation + ".");
        }
        if (!isApprovedEwa(member.ewaStatus())) {
            actions.add("Submit or complete EWA approval before final booking.");
        }
        if (actions.isEmpty()) {
            actions.add("Review recommendation evidence and proceed through EWA approval before booking.");
        }
        return distinct(actions);
    }

    private String memberRiskSummary(MemberEvidence member) {
        List<String> risks = new ArrayList<>();
        if (!member.missingRequiredSkills().isEmpty()) {
            risks.add("missing required skills: " + value(member.missingRequiredSkills()));
        }
        if (!member.missingDesiredSkills().isEmpty()) {
            risks.add("missing desired skills: " + value(member.missingDesiredSkills()));
        }
        if (isPositive(member.fteGap())) {
            risks.add("FTE gap: " + value(member.fteGap()));
        }
        if (StringUtils.hasText(member.blockingReason())) {
            risks.add("blocking reason: " + member.blockingReason());
        }
        if (StringUtils.hasText(member.constraint())) {
            risks.add("constraint: " + member.constraint());
        }
        if (risks.isEmpty()) {
            risks.add("No major risks identified from stored evidence.");
        }
        return String.join("; ", distinct(risks)) + ".";
    }

    private String domainProjectEvidence(MemberEvidence member) {
        List<String> evidence = new ArrayList<>();
        String primaryDomain = textMap(member.employee(), "primaryDomain");
        String secondaryDomain = textMap(member.employee(), "secondaryDomain");
        if (StringUtils.hasText(primaryDomain)) {
            evidence.add(primaryDomain);
        }
        if (StringUtils.hasText(secondaryDomain)) {
            evidence.add(secondaryDomain);
        }
        member.projectHistoryEvidence().stream()
                .map(project -> firstNonBlank(textMap(project, "projectName"), textMap(project, "domain")))
                .filter(StringUtils::hasText)
                .limit(3)
                .forEach(evidence::add);
        return evidence.isEmpty() ? NOT_SUPPLIED : String.join(", ", distinct(evidence));
    }

    private JsonNode findOptionNode(List<JsonNode> nodes, OptionEvidence option, int index) {
        for (JsonNode node : nodes) {
            if (Objects.equals(option.optionId(), text(node, "optionId"))) {
                return node;
            }
        }
        for (JsonNode node : nodes) {
            if (Objects.equals(option.optionType(), text(node, "optionType"))) {
                return node;
            }
        }
        return index < nodes.size() ? nodes.get(index) : null;
    }

    private JsonNode findMemberNode(List<JsonNode> nodes, MemberEvidence member, int index) {
        for (JsonNode node : nodes) {
            if (Objects.equals(member.employeeId(), text(node, "employeeId"))
                    && Objects.equals(member.opportunityRoleId(), text(node, "opportunityRoleId"))) {
                return node;
            }
        }
        for (JsonNode node : nodes) {
            if (Objects.equals(member.employeeId(), text(node, "employeeId"))) {
                return node;
            }
        }
        return index < nodes.size() ? nodes.get(index) : null;
    }

    private String requiredText(JsonNode node, String fieldName, String context) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("OpenAI response missed " + fieldName + " for " + context + ".");
        }
        return value;
    }

    private List<String> requiredList(JsonNode node, String fieldName, String context) {
        List<String> values = stringList(node, fieldName);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("OpenAI response missed " + fieldName + " for " + context + ".");
        }
        return values;
    }

    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                trimmed = trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        int firstObject = trimmed.indexOf('{');
        int lastObject = trimmed.lastIndexOf('}');
        if (firstObject >= 0 && lastObject >= firstObject) {
            return trimmed.substring(firstObject, lastObject + 1);
        }
        return trimmed;
    }

    private List<JsonNode> jsonArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    private String text(JsonNode root, String fieldName) {
        if (root == null || root.isMissingNode() || !root.has(fieldName) || root.get(fieldName).isNull()) {
            return null;
        }
        JsonNode value = root.get(fieldName);
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return clean(value.asText());
        }
        return null;
    }

    private List<String> stringList(JsonNode root, String fieldName) {
        if (root == null || root.isMissingNode() || !root.has(fieldName) || root.get(fieldName).isNull()) {
            return List.of();
        }
        JsonNode value = root.get(fieldName);
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> {
                String text = clean(item.asText(null));
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            });
            return values;
        }
        String text = clean(value.asText(null));
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    private String value(Object value) {
        if (value == null) {
            return NOT_SUPPLIED;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                String text = clean(item.toString());
                if (StringUtils.hasText(text)) {
                    values.add(text);
                }
            }
            return values.isEmpty() ? NOT_SUPPLIED : String.join(", ", distinct(values));
        }
        String text = clean(value.toString());
        return StringUtils.hasText(text) ? text : NOT_SUPPLIED;
    }

    private String textMap(Map<String, Object> values, String key) {
        if (values == null || values.get(key) == null) {
            return null;
        }
        return clean(values.get(key).toString());
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isTruthy(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = normalize(value);
        return normalized.equals("yes") || normalized.equals("true") || normalized.equals("y");
    }

    private boolean isApprovedEwa(String ewaStatus) {
        if (!StringUtils.hasText(ewaStatus)) {
            return false;
        }
        String normalized = normalize(ewaStatus);
        return normalized.contains("approved") || normalized.contains("booked") || normalized.contains("complete");
    }

    private boolean isFlexibleLocation(String locationPreference) {
        String normalized = normalize(locationPreference);
        return normalized.contains("any") || normalized.contains("remote") || normalized.contains("flexible");
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> distinct(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 1200 ? trimmed : trimmed.substring(0, 1200);
    }

    private String cleanError(RuntimeException ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message) && ex.getCause() != null) {
            message = ex.getCause().getMessage();
        }
        return clean(message);
    }

    private String configuredModel() {
        try {
            return openAiClient.getConfiguredModel();
        } catch (RuntimeException ex) {
            return FALLBACK_MODEL;
        }
    }
}
