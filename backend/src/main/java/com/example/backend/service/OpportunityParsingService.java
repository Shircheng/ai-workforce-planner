package com.example.backend.service;

import com.example.backend.dto.opportunity.OpportunityCommitRequest;
import com.example.backend.dto.opportunity.OpportunityParseResponse;
import com.example.backend.dto.opportunity.OpportunityRequest;
import com.example.backend.dto.opportunity.ValidationIssue;
import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityRole;
import com.example.backend.repository.OpportunityRepository;
import com.example.backend.repository.OpportunityRoleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpportunityParsingService {
    private static final String PENDING_ID = "PENDING";
    private static final String SOURCE_OPENAI = "openai";
    private static final String SOURCE_LOCAL_FALLBACK = "local-fallback";
    private static final String SOURCE_STORED = "stored";
    private static final String UNKNOWN = "Unknown";
    private static final String MEDIUM = "Medium";
    private static final String CONFIRM_WITH_REQUESTER = "Confirm with requester";
    private static final BigDecimal DEFAULT_MIN_INDIVIDUAL_FTE = BigDecimal.valueOf(1.0);
    private final OpportunityRepository opportunityRepository;
    private final OpportunityRoleRepository opportunityRoleRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String openAiApiKey;
    private final String openAiModel;

    public OpportunityParsingService(OpportunityRepository opportunityRepository, OpportunityRoleRepository opportunityRoleRepository, ObjectMapper objectMapper, @Value(value="${openai.api-key}") String openAiApiKey, @Value(value="${openai.model}") String openAiModel) {
        this.opportunityRepository = opportunityRepository;
        this.opportunityRoleRepository = opportunityRoleRepository;
        this.objectMapper = objectMapper;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.restClient = RestClient.builder().baseUrl("https://api.openai.com/v1").defaultHeader("Content-Type", new String[]{"application/json"}).build();
    }

    public OpportunityParseResponse parseAndValidate(OpportunityRequest request) {
        ParsedResult parsedResult = this.parseOpportunity(request);
        ParsedOpportunity merged = this.mergeUserFields(request, parsedResult.opportunity());
        List<ValidationIssue> validationIssues = this.validateParsedOpportunity(merged);
        this.rejectOnErrors(validationIssues);
        Opportunity opportunity = this.toOpportunity(PENDING_ID, request, merged);
        List<OpportunityRole> roles = this.toRoles(PENDING_ID, opportunity, merged.roles(), request.getStatement());
        validationIssues.addAll(this.validatePreviewRoles(roles));
        return new OpportunityParseResponse(opportunity, roles, validationIssues, parsedResult.source());
    }

    public OpportunityParseResponse storeForRecommender(OpportunityCommitRequest request) {
        List<ValidationIssue> validationIssues = this.validateCommitRequest(request);
        this.rejectOnErrors(validationIssues);
        String opportunityId = this.nextOpportunityId();
        List<String> opportunityRoleIds = this.nextOpportunityRoleIds(request.getRoles().size());
        Opportunity storedOpportunity = this.toStoredOpportunity(opportunityId, request.getOpportunity());
        List<OpportunityRole> storedRoles = this.toStoredRoles(opportunityId, storedOpportunity, request.getRoles(), opportunityRoleIds);
        this.opportunityRepository.save(storedOpportunity);
        this.opportunityRoleRepository.saveAll(storedRoles);
        return new OpportunityParseResponse(storedOpportunity, storedRoles, validationIssues, SOURCE_STORED);
    }

    private synchronized String nextOpportunityId() {
        int maxValue = 0;
        String prefix = "OPP-";
        int width = 3;
        Pattern idPattern = Pattern.compile("^(.*?)(\\d+)$");
        for (Opportunity opportunity : this.opportunityRepository.findAll()) {
            int numericValue;
            Matcher matcher;
            String currentId = opportunity.getOpportunityId();
            if (currentId == null || currentId.isBlank() || !(matcher = idPattern.matcher(currentId.trim())).matches()) continue;
            try {
                numericValue = Integer.parseInt(matcher.group(2));
            }
            catch (NumberFormatException ignored) {
                continue;
            }
            if (numericValue <= maxValue) continue;
            maxValue = numericValue;
            prefix = matcher.group(1);
            width = matcher.group(2).length();
        }
        return prefix + String.format(Locale.ROOT, "%0" + width + "d", maxValue + 1);
    }

    private synchronized List<String> nextOpportunityRoleIds(int count) {
        int maxValue = 0;
        Pattern roleIdPattern = Pattern.compile("^OPR-(\\d+)$");
        for (OpportunityRole role : this.opportunityRoleRepository.findAll()) {
            Matcher matcher;
            String currentId = role.getOpportunityRoleId();
            if (currentId == null || currentId.isBlank() || !(matcher = roleIdPattern.matcher(currentId.trim())).matches()) continue;
            try {
                maxValue = Math.max(maxValue, Integer.parseInt(matcher.group(1)));
            }
            catch (NumberFormatException numberFormatException) {}
        }
        ArrayList<String> ids = new ArrayList<String>();
        for (int i = 1; i <= count; ++i) {
            ids.add("OPR-" + String.format(Locale.ROOT, "%04d", maxValue + i));
        }
        return ids;
    }

    private ParsedResult parseOpportunity(OpportunityRequest request) {
        if (!this.hasOpenAiKey()) {
            return new ParsedResult(this.fallbackParse(request, "OpenAI API key is not configured, so a local fallback parser was used."), SOURCE_LOCAL_FALLBACK);
        }
        try {
            String outputText = this.extractOutputText(this.callOpenAi(request));
            return new ParsedResult((ParsedOpportunity)this.objectMapper.readValue(outputText, ParsedOpportunity.class), SOURCE_OPENAI);
        }
        catch (Exception ex) {
            return new ParsedResult(this.fallbackParse(request, this.openAiFallbackMessage(ex)), SOURCE_LOCAL_FALLBACK);
        }
    }

    private Map callOpenAi(OpportunityRequest request) {
        return (Map)((RestClient.RequestBodySpec)((RestClient.RequestBodySpec)this.restClient.post().uri("/responses", new Object[0])).header("Authorization", new String[]{"Bearer " + this.openAiApiKey})).body(this.openAiRequest(request)).retrieve().body(Map.class);
    }

    private Map<String, Object> openAiRequest(OpportunityRequest request) {
        Map<String, Object> roleSchema = Map.of("type", "object", "additionalProperties", false, "properties", Map.ofEntries(Map.entry("roleName", Map.of("type", "string")), Map.entry("disciplineOrDepartment", Map.of("type", "string")), Map.entry("gradePreference", Map.of("type", "string")), Map.entry("requiredSkills", Map.of("type", "array", "items", Map.of("type", "string"))), Map.entry("desiredSkills", Map.of("type", "array", "items", Map.of("type", "string"))), Map.entry("domainExperienceRequired", Map.of("type", "string")), Map.entry("locationPreference", Map.of("type", "string")), Map.entry("durationWeeks", Map.of("type", "integer")), Map.entry("fteRequired", Map.of("type", "number")), Map.entry("priority", Map.of("type", "string")), Map.entry("flexibilityNotes", Map.of("type", "string")), Map.entry("minimumIndividualFTE", Map.of("type", "number")), Map.entry("canCombineCandidates", Map.of("type", "string", "enum", List.of("Yes", "No")))), "required", List.of("roleName", "disciplineOrDepartment", "gradePreference", "requiredSkills", "desiredSkills", "domainExperienceRequired", "locationPreference", "durationWeeks", "fteRequired", "priority", "flexibilityNotes", "minimumIndividualFTE", "canCombineCandidates"));
        Map<String, Object> schema = Map.of("type", "object", "additionalProperties", false, "properties", Map.of("opportunityName", Map.of("type", "string"), "domain", Map.of("type", "string"), "projectType", Map.of("type", "string"), "deliveryRisk", Map.of("type", "string"), "roles", Map.of("type", "array", "items", roleSchema), "validationNotes", Map.of("type", "array", "items", Map.of("type", "string"))), "required", List.of("opportunityName", "domain", "projectType", "deliveryRisk", "roles", "validationNotes"));
        return Map.of("model", this.openAiModel, "input", List.of(Map.of("role", "system", "content", "Parse workforce staffing opportunity requests into auditable JSON. The structured request fields are authoritative context and must be considered together with the statement. If the statement conflicts with a structured field, use the structured field. Use these dataset discipline labels when they apply: Engineering, Creative Services, Product, Business Analysis, Quality Engineering, Data & AI, Architecture, Infrastructure & Cloud, Delivery Management, Security. For example, Product Designer, UX Designer, UX Researcher, Service Designer, and Design Engineer belong to Creative Services. Use these gradePreference labels when stated: Associate Consultant, Consultant, Intermediate, Junior, Lead, Lead Consultant, Manager, Mid, Principal Consultant, Senior, Senior Consultant, Senior Manager. Do not infer gradePreference from a role name alone; for example, Delivery Manager is a role name, not a Manager grade, unless the statement says manager-level or manager-grade. Put a skill in desiredSkills only when the statement clearly says it is optional, additional, preferred, nice-to-have, bonus, or desirable; do not repeat the same skill in both requiredSkills and desiredSkills. When the statement says a list of skills are optional, those exact skills must be desiredSkills, not requiredSkills. For domainExperienceRequired, output only a valid domain label from the people dataset primary or secondary domains, such as Banking, Financial Services, Healthcare, Insurance, Public Sector, Retail, Telecommunications, Internal Platforms, Energy, Travel, Education, Legacy Platforms, Logistics, Media, or Payments; never output Yes or No for domainExperienceRequired. For fteRequired, use the stated role FTE when provided; if no FTE is stated for a role, output 1. For canCombineCandidates, output Yes only when the statement explicitly says candidates can be combined, split, or staffed by multiple candidates; otherwise output No. For locationPreference, output only a country, region, or dataset-style location label such as Australia / AEST/AEDT, Australia or India, APAC, India, or Malaysia. Do not output remote, hybrid, part-time, or availability phrases in locationPreference; put those role-specific staffing notes into flexibilityNotes instead. If no role-specific location is stated, use the structured request country and never output phrases like No specific location stated. Flexibility notes should only contain special staffing notes that are not already represented by another role field, including remote, hybrid, part-time, and must-be-available-by-start-date requirements. Assign a flexibility note only to the role explicitly named in the sentence; for example, product designer must available when project start applies only to Product Designer. Do not copy remote, hybrid, part-time, or availability notes to other roles unless those roles are explicitly named. Use concise canonical wording for working-mode notes, such as Remote allowed, Hybrid allowed, or Part-time allowed. Do not put locationPreference, startDate, gradePreference, canCombineCandidates, combined-candidates staffing, minimumIndividualFTE, or FTE statements into flexibilityNotes. Do not add validation notes for missing request-level fields when those values are present in the structured request. Do not add validation notes only because optional role details were not detected from the statement. Add validation notes only for true contradictions, impossible values, or important role requirements that remain unclear after considering both the statement and structured request fields. Do not add validation notes for successful consistency checks, such as when the requested role count matches the parsed role count."), Map.of("role", "user", "content", this.opportunityRequestContext(request))), "text", Map.of("format", Map.of("type", "json_schema", "name", "opportunity_requirement_parse", "strict", true, "schema", schema)));
    }

    private String openAiFallbackMessage(Exception ex) {
        String message = Objects.toString(ex.getMessage(), "");
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("insufficient_quota") || normalized.contains("too many requests") || normalized.contains("current quota") || normalized.contains("429")) {
            return "OpenAI quota is unavailable, so a local fallback parser was used. Please check your OpenAI billing or quota if you want AI parsing.";
        }
        return "OpenAI parsing was unavailable, so a local fallback parser was used.";
    }

    private String opportunityRequestContext(OpportunityRequest request) {
        return "Opportunity statement:\n" + Objects.toString(request.getStatement(), "") + "\n\nStructured request fields supplied by the user:\nOpportunity brief: " + Objects.toString(request.getOpportunityBrief(), "") + "\nOpportunity name: " + Objects.toString(request.getOpportunityName(), "") + "\nClient name: " + Objects.toString(request.getClientName(), "") + "\nClient type: " + Objects.toString(request.getClientType(), "") + "\nDomain: " + Objects.toString(request.getDomain(), "") + "\nRegion: " + Objects.toString(request.getRegion(), "") + "\nCountry: " + Objects.toString(request.getCountry(), "") + "\nCity: " + Objects.toString(request.getCity(), "") + "\nProbability: " + Objects.toString(request.getProbability(), "") + "\nExpected start date: " + Objects.toString(request.getExpectedStartDate(), "") + "\nDuration weeks: " + Objects.toString(request.getDurationWeeks(), "") + "\nCommercial priority: " + Objects.toString(request.getCommercialPriority(), "") + "\nTimezone preference: " + Objects.toString(request.getTimezonePreference(), "");
    }

    private String extractOutputText(Map response) throws JsonProcessingException {
        JsonNode root = this.objectMapper.valueToTree((Object)response);
        for (JsonNode output : root.path("output")) {
            for (JsonNode content : output.path("content")) {
                if (!content.has("text")) continue;
                return content.path("text").asText();
            }
        }
        if (root.has("output_text")) {
            return root.path("output_text").asText();
        }
        throw new JsonProcessingException("OpenAI response did not contain output text"){};
    }

    private ParsedOpportunity mergeUserFields(OpportunityRequest request, ParsedOpportunity parsed) {
        return new ParsedOpportunity(this.firstNonBlank(request.getOpportunityName(), parsed.opportunityName(), "Draft staffing opportunity"), this.cleanDomain(this.firstNonBlank(request.getDomain(), parsed.domain(), this.inferDomain(request.getStatement()), "General")), this.firstNonBlank(parsed.projectType(), "Delivery"), this.firstNonBlank(parsed.deliveryRisk(), "Medium"), parsed.roles(), this.filterValidationNotes(request, parsed.validationNotes()));
    }

    private List<String> filterValidationNotes(OpportunityRequest request, List<String> notes) {
        return notes.stream().filter(note -> this.shouldKeepValidationNote(request, (String)note)).toList();
    }

    private boolean shouldKeepValidationNote(OpportunityRequest request, String note) {
        String value = Objects.toString(note, "").toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return false;
        }
        if (value.contains("interpreted as") && !value.contains("conflict") && !value.contains("contradict") && !value.contains("unclear")) {
            return false;
        }
        if ((value.contains("names") || value.contains("states") || value.contains("says")) && value.contains("roles") && (value.contains("also resolves") || value.contains("provided role list"))) {
            return false;
        }
        if (value.contains("senior level") && value.contains("preferred") && !value.contains("conflict") && !value.contains("contradict") && !value.contains("unclear")) {
            return false;
        }
        if ((value.contains("explicitly combinable") || value.contains("combinable")) && (value.contains("minimum individual fte") || value.contains("other roles are not stated"))) {
            return false;
        }
        if ((value.contains("working-mode") || value.contains("working mode")) && value.contains("availability notes") && value.contains("not stated")) {
            return false;
        }
        if (value.contains("appears consistent") || value.contains("is consistent") || value.contains("are consistent") || value.contains("matches the parsed") || value.contains("roles") && value.contains("parsed") && !value.contains("but only") && !value.contains("missing") && !value.contains("unclear")) {
            return false;
        }
        if (request.getDurationWeeks() != null && value.contains("duration")) {
            return false;
        }
        if (request.getExpectedStartDate() != null && (value.contains("start") || value.contains("date"))) {
            return false;
        }
        if (request.getDomain() != null && !request.getDomain().isBlank() && value.contains("domain")) {
            return false;
        }
        if (request.getCountry() != null && !request.getCountry().isBlank() && (value.contains("location") || value.contains("country") || value.contains("city") || value.contains("region"))) {
            return false;
        }
        if (request.getCommercialPriority() != null && !request.getCommercialPriority().isBlank() && value.contains("priority")) {
            return false;
        }
        if (request.getTimezonePreference() != null && !request.getTimezonePreference().isBlank() && value.contains("timezone")) {
            return false;
        }
        return !value.contains("skill") && !value.contains("role detail") && !value.contains("grade");
    }

    private List<ValidationIssue> validateParsedOpportunity(ParsedOpportunity parsed) {
        ArrayList<ValidationIssue> issues = new ArrayList<ValidationIssue>();
        if (parsed.opportunityName() == null || parsed.opportunityName().isBlank()) {
            issues.add(new ValidationIssue("opportunityName", "error", "Opportunity name is required."));
        }
        if (parsed.roles().isEmpty()) {
            issues.add(new ValidationIssue("roles", "error", "At least one opportunity role is required."));
        }
        for (int i = 0; i < parsed.roles().size(); ++i) {
            ParsedRole role = parsed.roles().get(i);
            if (role.fteRequired() != null && role.fteRequired().compareTo(BigDecimal.ZERO) < 0) {
                issues.add(new ValidationIssue("roles[" + i + "].fteRequired", "error", "FTE required must be greater than zero."));
            }
        }
        parsed.validationNotes().forEach(note -> issues.add(new ValidationIssue("statement", "warning", (String)note)));
        return issues;
    }

    private List<ValidationIssue> validatePreviewRoles(List<OpportunityRole> roles) {
        ArrayList<ValidationIssue> issues = new ArrayList<ValidationIssue>();
        for (int i = 0; i < roles.size(); ++i) {
            OpportunityRole role = roles.get(i);
            if (this.isGenericRoleName(role.getRoleName())) {
                issues.add(new ValidationIssue("roles[" + i + "].roleName", "warning", "Role name needed before options can be generated."));
            }
            if (!this.hasMeaningfulGradePreference(role.getGradePreference())) {
                issues.add(new ValidationIssue("roles[" + i + "].gradePreference", "warning", "Grade preference needed before options can be generated."));
            }
            if (!this.hasMeaningfulSkills(role.getRequiredSkills())) {
                issues.add(new ValidationIssue("roles[" + i + "].requiredSkills", "warning", "Required skills needed before options can be generated."));
            }
            if (this.hasMeaningfulSkills(role.getDesiredSkills())) continue;
            issues.add(new ValidationIssue("roles[" + i + "].desiredSkills", "warning", "Desired skills needed before options can be generated."));
        }
        return issues;
    }

    private boolean isMeaningfulSkill(String skill) {
        if (skill == null || skill.isBlank()) {
            return false;
        }
        String normalized = skill.toLowerCase(Locale.ROOT);
        return !"to be confirmed".equals(normalized) && !"tbd".equals(normalized) && !"unknown".equals(normalized);
    }

    private boolean hasMeaningfulSkills(List<String> skills) {
        return skills != null && skills.stream().anyMatch(this::isMeaningfulSkill);
    }

    private List<ValidationIssue> validateCommitRequest(OpportunityCommitRequest request) {
        ArrayList<ValidationIssue> issues = new ArrayList<ValidationIssue>();
        Opportunity opportunity = request.getOpportunity();
        this.addRequiredIssue(issues, "opportunity.opportunityName", opportunity.getOpportunityName(), "Opportunity name is required.");
        this.addRequiredIssue(issues, "opportunity.clientName", opportunity.getClientName(), "Client name is required.");
        this.addRequiredIssue(issues, "opportunity.domain", opportunity.getDomain(), "Domain is required.");
        this.addRequiredIssue(issues, "opportunity.country", opportunity.getCountry(), "Country is required.");
        this.addRequiredIssue(issues, "opportunity.commercialPriority", opportunity.getCommercialPriority(), "Commercial priority is required.");
        this.addRequiredIssue(issues, "opportunity.opportunityBrief", opportunity.getOpportunityBrief(), "Opportunity brief is required.");
        if (opportunity.getProbability() == null) {
            issues.add(new ValidationIssue("opportunity.probability", "error", "Probability is required."));
        }
        if (opportunity.getExpectedStartDate() == null) {
            issues.add(new ValidationIssue("opportunity.expectedStartDate", "error", "Expected start date is required."));
        }
        if (opportunity.getDurationWeeks() == null) {
            issues.add(new ValidationIssue("opportunity.durationWeeks", "error", "Duration weeks is required."));
        }
        for (int i = 0; i < request.getRoles().size(); ++i) {
            OpportunityRole role = request.getRoles().get(i);
            if (role.getFteRequired() == null || role.getFteRequired().compareTo(BigDecimal.ZERO) <= 0) {
                issues.add(new ValidationIssue("roles[" + i + "].fteRequired", "error", "FTE required must be greater than zero."));
            }
            if (this.isGenericRoleName(role.getRoleName())) {
                issues.add(new ValidationIssue("roles[" + i + "].roleName", "error", "Role name needed before options can be generated."));
            }
            if (!this.hasMeaningfulGradePreference(role.getGradePreference())) {
                issues.add(new ValidationIssue("roles[" + i + "].gradePreference", "error", "Grade preference needed before options can be generated."));
            }
            if (!this.hasMeaningfulSkills(role.getRequiredSkills())) {
                issues.add(new ValidationIssue("roles[" + i + "].requiredSkills", "error", "Required skills needed before options can be generated."));
            }
            if (this.hasMeaningfulSkills(role.getDesiredSkills())) continue;
            issues.add(new ValidationIssue("roles[" + i + "].desiredSkills", "error", "Desired skills needed before options can be generated."));
        }
        return issues;
    }

    private void addRequiredIssue(List<ValidationIssue> issues, String field, String value, String message) {
        if (value == null || value.isBlank()) {
            issues.add(new ValidationIssue(field, "error", message));
        }
    }

    private void rejectOnErrors(List<ValidationIssue> validationIssues) {
        List<String> errorMessages = validationIssues.stream().filter(issue -> "error".equals(issue.severity())).map(ValidationIssue::message).distinct().toList();
        if (!errorMessages.isEmpty()) {
            throw new ResponseStatusException((HttpStatusCode)HttpStatus.BAD_REQUEST, String.join((CharSequence)" ", errorMessages));
        }
    }

    private Opportunity toOpportunity(String opportunityId, OpportunityRequest request, ParsedOpportunity parsed) {
        return Opportunity.builder().opportunityId(opportunityId).opportunityName(parsed.opportunityName()).clientName(request.getClientName()).clientType(this.firstNonBlank(request.getClientType(), "Unknown")).region(this.firstNonBlank(request.getRegion(), "Unknown")).country(request.getCountry()).city(this.firstNonBlank(request.getCity(), "Unknown")).domain(this.cleanDomain(parsed.domain())).stage("Discovery").probability(request.getProbability()).expectedStartDate(request.getExpectedStartDate()).durationWeeks(request.getDurationWeeks()).commercialPriority(request.getCommercialPriority()).deliveryRisk(parsed.deliveryRisk()).opportunityBrief(request.getOpportunityBrief()).timezonePreference(this.firstNonBlank(request.getTimezonePreference(), "Unknown")).build();
    }

    private Opportunity toStoredOpportunity(String opportunityId, Opportunity submitted) {
        return Opportunity.builder().opportunityId(opportunityId).opportunityName(this.firstNonBlank(submitted.getOpportunityName(), "Draft staffing opportunity")).clientName(this.firstNonBlank(submitted.getClientName(), "Client opportunity")).clientType(this.firstNonBlank(submitted.getClientType(), "Unknown")).region(this.firstNonBlank(submitted.getRegion(), "Unknown")).country(this.firstNonBlank(submitted.getCountry(), "Unknown")).city(this.firstNonBlank(submitted.getCity(), "Unknown")).domain(this.cleanDomain(this.firstNonBlank(submitted.getDomain(), "Unknown"))).stage("Qualified").probability(this.defaultDecimal(submitted.getProbability(), BigDecimal.valueOf(0.65))).expectedStartDate(submitted.getExpectedStartDate() == null ? LocalDate.now().plusDays(30L) : submitted.getExpectedStartDate()).durationWeeks(this.defaultInteger(submitted.getDurationWeeks(), 24)).commercialPriority(this.firstNonBlank(submitted.getCommercialPriority(), "Medium")).deliveryRisk(this.firstNonBlank(submitted.getDeliveryRisk(), "Medium")).opportunityBrief(this.firstNonBlank(submitted.getOpportunityBrief(), "")).timezonePreference(this.firstNonBlank(submitted.getTimezonePreference(), "Unknown")).build();
    }

    private List<OpportunityRole> toRoles(String opportunityId, Opportunity opportunity, List<ParsedRole> parsedRoles, String statement) {
        ArrayList<OpportunityRole> roles = new ArrayList<OpportunityRole>();
        StatementSkillHints skillHints = this.extractStatementSkillHints(statement);
        List<String> statementRoles = this.extractRoleNames(statement);
        for (int i = 0; i < parsedRoles.size(); ++i) {
            ParsedRole parsedRole = parsedRoles.get(i);
            String roleName = this.cleanRoleName(parsedRole.roleName(), statementRoles, i);
            String gradePreference = this.cleanGradePreference(parsedRole.gradePreference(), statement, roleName);
            List<String> requiredSkills = this.reconciledRequiredSkills(parsedRole, roleName, skillHints);
            List<String> desiredSkills = this.reconciledDesiredSkills(parsedRole, roleName, skillHints, requiredSkills);
            roles.add(this.toPreviewRole(opportunityId, opportunity, parsedRole, statement, roleName, gradePreference, requiredSkills, desiredSkills, i));
        }
        return roles;
    }

    private OpportunityRole toPreviewRole(String opportunityId, Opportunity opportunity, ParsedRole parsedRole, String statement, String roleName, String gradePreference, List<String> requiredSkills, List<String> desiredSkills, int index) {
        return OpportunityRole.builder()
                .opportunityRoleId(opportunityId + "-ROLE-" + (index + 1))
                .opportunityId(opportunityId)
                .roleName(roleName)
                .disciplineOrDepartment(this.normalizeDiscipline(roleName, parsedRole.disciplineOrDepartment()))
                .gradePreference(gradePreference)
                .requiredSkills(requiredSkills)
                .desiredSkills(desiredSkills)
                .domainExperienceRequired(this.cleanRoleDomain(parsedRole.domainExperienceRequired(), opportunity))
                .locationPreference(this.cleanLocationPreference(parsedRole.locationPreference(), opportunity, statement))
                .startDate(opportunity.getExpectedStartDate())
                .durationWeeks(this.defaultInteger(parsedRole.durationWeeks(), opportunity.getDurationWeeks()))
                .fteRequired(this.cleanFteRequired(roleName, parsedRole.fteRequired(), statement))
                .priority(this.cleanRolePriority(parsedRole.priority(), opportunity.getCommercialPriority()))
                .flexibilityNotes(this.roleSpecificFlexibilityNotes(roleName, statement, parsedRole.flexibilityNotes()))
                .minimumIndividualFTE(this.defaultDecimal(parsedRole.minimumIndividualFTE(), DEFAULT_MIN_INDIVIDUAL_FTE))
                .canCombineCandidates(this.cleanCanCombineCandidates(parsedRole.canCombineCandidates(), roleName, statement))
                .build();
    }

    private List<OpportunityRole> toStoredRoles(String opportunityId, Opportunity opportunity, List<OpportunityRole> submittedRoles, List<String> opportunityRoleIds) {
        ArrayList<OpportunityRole> roles = new ArrayList<OpportunityRole>();
        for (int i = 0; i < submittedRoles.size(); ++i) {
            OpportunityRole submittedRole = submittedRoles.get(i);
            roles.add(this.toStoredRole(opportunityId, opportunity, submittedRole, opportunityRoleIds.get(i), i));
        }
        return roles;
    }

    private OpportunityRole toStoredRole(String opportunityId, Opportunity opportunity, OpportunityRole submittedRole, String opportunityRoleId, int index) {
        String roleName = this.firstNonBlank(submittedRole.getRoleName(), "Role to confirm " + (index + 1));
        return OpportunityRole.builder()
                .opportunityRoleId(opportunityRoleId)
                .opportunityId(opportunityId)
                .roleName(roleName)
                .disciplineOrDepartment(this.normalizeDiscipline(roleName, submittedRole.getDisciplineOrDepartment()))
                .gradePreference(this.cleanStoredGradePreference(submittedRole.getGradePreference()))
                .requiredSkills(this.cleanSkills(submittedRole.getRequiredSkills()))
                .desiredSkills(this.removeDuplicateSkills(submittedRole.getRequiredSkills(), submittedRole.getDesiredSkills()))
                .domainExperienceRequired(this.cleanRoleDomain(submittedRole.getDomainExperienceRequired(), opportunity))
                .locationPreference(this.cleanLocationPreference(submittedRole.getLocationPreference(), opportunity))
                .startDate(submittedRole.getStartDate() == null ? opportunity.getExpectedStartDate() : submittedRole.getStartDate())
                .durationWeeks(this.defaultInteger(submittedRole.getDurationWeeks(), opportunity.getDurationWeeks()))
                .fteRequired(this.defaultDecimal(submittedRole.getFteRequired(), BigDecimal.ONE))
                .priority(this.cleanRolePriority(submittedRole.getPriority(), opportunity.getCommercialPriority()))
                .flexibilityNotes(this.cleanFlexibilityNotes(submittedRole.getFlexibilityNotes()))
                .minimumIndividualFTE(this.defaultDecimal(submittedRole.getMinimumIndividualFTE(), DEFAULT_MIN_INDIVIDUAL_FTE))
                .canCombineCandidates("Yes".equalsIgnoreCase(submittedRole.getCanCombineCandidates()) ? "Yes" : "No")
                .build();
    }

    private ParsedOpportunity fallbackParse(OpportunityRequest request, String validationNote) {
        String statement = request.getStatement();
        String domain = this.cleanDomain(this.firstNonBlank(request.getDomain(), this.inferDomain(statement)));
        String location = this.firstNonBlank(this.cleanSpecificLocation(this.inferLocation(statement)), request.getCountry(), request.getCity(), request.getRegion());
        List<String> statementRoles = this.extractRoleNames(statement);
        List<ParsedRole> roles = statementRoles.isEmpty()
                ? List.of(this.unknownFallbackRole(request, statement, domain, location))
                : statementRoles.stream().map(roleName -> this.fallbackRole(request, statement, domain, location, roleName)).toList();
        return new ParsedOpportunity(request.getOpportunityName(), domain, UNKNOWN, MEDIUM, roles, new ArrayList<String>(List.of(validationNote)));
    }

    private ParsedRole unknownFallbackRole(OpportunityRequest request, String statement, String domain, String location) {
        List<String> skills = this.inferSkills(statement);
        int teamSize = this.inferTeamSize(statement);
        return new ParsedRole(UNKNOWN, UNKNOWN, "Any", skills, List.of(), domain, location, request.getDurationWeeks(), BigDecimal.valueOf(teamSize), request.getCommercialPriority(), CONFIRM_WITH_REQUESTER, DEFAULT_MIN_INDIVIDUAL_FTE, teamSize > 1 ? "Yes" : "No");
    }

    private ParsedRole fallbackRole(OpportunityRequest request, String statement, String domain, String location, String roleName) {
        return new ParsedRole(roleName, this.normalizeDiscipline(roleName, UNKNOWN), "Any", List.of(), List.of(), domain, location, request.getDurationWeeks(), BigDecimal.ONE, request.getCommercialPriority(), CONFIRM_WITH_REQUESTER, this.defaultDecimal(this.statementMinimumIndividualFteForRole(roleName, statement), DEFAULT_MIN_INDIVIDUAL_FTE), "No");
    }

    private String normalizeDiscipline(String roleName, String parsedDiscipline) {
        String normalizedRole = this.normalizeText(roleName);
        Map datasetDisciplines = Map.ofEntries(Map.entry("ai engineer", "Data & AI"), Map.entry("backend engineer", "Engineering"), Map.entry("business analyst", "Business Analysis"), Map.entry("cloud engineer", "Infrastructure & Cloud"), Map.entry("data analyst", "Data & AI"), Map.entry("data architect", "Architecture"), Map.entry("data engineer", "Data & AI"), Map.entry("data scientist", "Data & AI"), Map.entry("delivery manager", "Delivery Management"), Map.entry("design engineer", "Creative Services"), Map.entry("frontend engineer", "Engineering"), Map.entry("full stack engineer", "Engineering"), Map.entry("mobile engineer", "Engineering"), Map.entry("platform engineer", "Infrastructure & Cloud"), Map.entry("product designer", "Creative Services"), Map.entry("product manager", "Product"), Map.entry("product owner", "Product"), Map.entry("product strategist", "Product"), Map.entry("qa automation engineer", "Quality Engineering"), Map.entry("qa engineer", "Quality Engineering"), Map.entry("quality lead", "Quality Engineering"), Map.entry("security consultant", "Security"), Map.entry("service designer", "Creative Services"), Map.entry("solution architect", "Architecture"), Map.entry("ux designer", "Creative Services"), Map.entry("ux researcher", "Creative Services"), Map.entry("workforce planning sme", "Delivery Management"));
        String mapped = (String)datasetDisciplines.get(normalizedRole);
        return this.firstNonBlank(mapped, parsedDiscipline, "Unknown");
    }

    private String cleanRoleName(String parsedRoleName, List<String> statementRoles, int index) {
        String parsed = this.canonicalRoleName(parsedRoleName);
        if (!this.isGenericRoleName(parsed)) {
            return parsed;
        }
        if (index < statementRoles.size()) {
            return statementRoles.get(index);
        }
        return "Unknown Role " + (index + 1);
    }

    private List<String> extractRoleNames(String statement) {
        ArrayList<String> roles = new ArrayList<String>();
        String normalized = this.normalizeText(statement);
        for (String role : this.roleCatalog()) {
            if (!this.statementMentionsCatalogRole(normalized, role)) continue;
            roles.add(role);
        }
        return roles;
    }

    private boolean statementMentionsCatalogRole(String normalizedStatement, String role) {
        String normalizedRole = this.normalizeText(role);
        return normalizedStatement.contains(normalizedRole) || normalizedRole.equals("delivery manager") && normalizedStatement.contains("delivery manger");
    }

    private List<String> roleCatalog() {
        return List.of("Delivery Manager", "Product Designer", "Backend Engineer", "Frontend Engineer", "Full Stack Engineer", "QA Engineer", "QA Automation Engineer", "Cloud Engineer", "Data Engineer", "Data Scientist", "Business Analyst", "Product Manager", "Product Owner", "Solution Architect", "Security Consultant", "UX Designer", "UX Researcher", "Service Designer");
    }

    private String canonicalRoleName(String roleName) {
        String cleaned = this.firstNonBlank(roleName);
        if (cleaned == null) {
            return "Unknown";
        }
        String normalized = this.normalizeText(cleaned);
        if (normalized.equals("delivery manger")) {
            return "Delivery Manager";
        }
        for (String role : this.roleCatalog()) {
            if (!normalized.equals(this.normalizeText(role))) continue;
            return role;
        }
        return cleaned.trim();
    }

    private boolean isGenericRoleName(String roleName) {
        String normalized = this.normalizeText(roleName);
        return normalized.isBlank() || normalized.equals("unknown") || normalized.equals("tbd") || normalized.equals("to be confirmed") || normalized.matches("role\\s*\\d+") || normalized.startsWith("role to confirm");
    }

    private String cleanGradePreference(String parsedGradePreference, String statement, String roleName) {
        if (!this.hasGradePreferenceLanguage(statement)) {
            return "Any";
        }
        return this.firstNonBlank(this.inferRoleGradePreference(roleName, statement), this.inferGeneralGradePreference(statement), this.canonicalGradePreference(parsedGradePreference), "Any");
    }

    private String cleanStoredGradePreference(String gradePreference) {
        return this.firstNonBlank(this.canonicalGradePreference(gradePreference), "Any");
    }

    private boolean hasMeaningfulGradePreference(String gradePreference) {
        String normalized = this.normalizeText(gradePreference);
        return !normalized.isBlank() && !normalized.equals("any") && !normalized.equals("unknown") && !normalized.equals("tbd") && !normalized.equals("to be confirmed") && !normalized.matches("\\d+(?:\\.\\d+)?");
    }

    private boolean hasGradePreferenceLanguage(String statement) {
        String normalized = this.normalizeText(statement);
        return Pattern.compile("\\b(senior|sr|lead|junior|jr|mid|middle|intermediate)\\b|\\b(associate|lead|principal|senior)\\s+consultant\\b|\\b(senior\\s+manager|manager\\s*(?:level|grade)|manager\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*manager)\\b|\\b(consultant\\s*(?:level|grade)|consultant\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*consultant)\\b", 2).matcher(normalized).find();
    }

    private String inferRoleGradePreference(String roleName, String statement) {
        String normalizedRole = this.normalizeText(roleName);
        if (normalizedRole.isBlank()) {
            return null;
        }
        for (String part : this.statementClauses(statement)) {
            String normalized = this.normalizeText(part);
            if (!this.statementPartMentionsRole(normalized, normalizedRole) || !this.hasGradePreferenceLanguage(part)) {
                continue;
            }
            String gradePreference = this.inferGradePreference(part);
            if (gradePreference != null) {
                return gradePreference;
            }
        }
        return null;
    }

    private String inferGeneralGradePreference(String statement) {
        for (String part : this.statementClauses(statement)) {
            if (!this.hasGradePreferenceLanguage(part) || this.statementPartMentionsAnyCatalogRole(part)) {
                continue;
            }
            String gradePreference = this.inferGradePreference(part);
            if (gradePreference != null) {
                return gradePreference;
            }
        }
        return null;
    }

    private boolean statementPartMentionsAnyCatalogRole(String statementPart) {
        String normalized = this.normalizeText(statementPart);
        for (String role : this.roleCatalog()) {
            if (this.statementMentionsCatalogRole(normalized, role)) {
                return true;
            }
        }
        return false;
    }

    private String inferGradePreference(String statement) {
        String normalized = this.normalizeText(statement);
        if (Pattern.compile("\\bsenior\\s+manager\\b", 2).matcher(normalized).find()) {
            return "Senior Manager";
        }
        if (Pattern.compile("\\b(manager\\s*(?:level|grade)|manager\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*manager)\\b", 2).matcher(normalized).find()) {
            return "Manager";
        }
        if (Pattern.compile("\\bprincipal\\s+consultant\\b", 2).matcher(normalized).find()) {
            return "Principal Consultant";
        }
        if (Pattern.compile("\\blead\\s+consultant\\b", 2).matcher(normalized).find()) {
            return "Lead Consultant";
        }
        if (Pattern.compile("\\bsenior\\s+consultant\\b", 2).matcher(normalized).find()) {
            return "Senior Consultant";
        }
        if (Pattern.compile("\\bassociate\\s+consultant\\b", 2).matcher(normalized).find()) {
            return "Associate Consultant";
        }
        if (Pattern.compile("\\b(consultant\\s*(?:level|grade)|consultant\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*consultant)\\b", 2).matcher(normalized).find()) {
            return "Consultant";
        }
        if (Pattern.compile("\\b(lead|lead\\s*(?:level|grade)|lead\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*lead)\\b", 2).matcher(normalized).find()) {
            return "Lead";
        }
        if (Pattern.compile("\\b(senior|sr|senior\\s*(?:level|grade)|senior\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*senior)\\b", 2).matcher(normalized).find()) {
            return "Senior";
        }
        if (Pattern.compile("\\b(junior|jr|junior\\s*(?:level|grade)|junior\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*junior)\\b", 2).matcher(normalized).find()) {
            return "Junior";
        }
        if (Pattern.compile("\\b(mid|middle|mid\\s*(?:level|grade)|mid\\s*-\\s*(?:level|grade)|grade\\s*[:=]?\\s*mid)\\b", 2).matcher(normalized).find()) {
            return "Mid";
        }
        if (Pattern.compile("\\bintermediate\\b", 2).matcher(normalized).find()) {
            return "Intermediate";
        }
        return null;
    }

    private String canonicalGradePreference(String gradePreference) {
        String normalized = this.normalizeText(gradePreference);
        if (normalized.contains("senior manager")) {
            return "Senior Manager";
        }
        if (normalized.equals("manager") || normalized.contains("manager level") || normalized.contains("manager grade")) {
            return "Manager";
        }
        if (normalized.contains("principal consultant")) {
            return "Principal Consultant";
        }
        if (normalized.contains("lead consultant")) {
            return "Lead Consultant";
        }
        if (normalized.contains("senior consultant")) {
            return "Senior Consultant";
        }
        if (normalized.contains("associate consultant")) {
            return "Associate Consultant";
        }
        if (normalized.equals("consultant") || normalized.contains("consultant level") || normalized.contains("consultant grade")) {
            return "Consultant";
        }
        if (normalized.equals("lead") || normalized.contains("lead level") || normalized.contains("lead grade")) {
            return "Lead";
        }
        if (normalized.equals("senior") || normalized.equals("sr") || normalized.contains("senior level") || normalized.contains("senior grade")) {
            return "Senior";
        }
        if (normalized.equals("junior") || normalized.equals("jr") || normalized.contains("junior level") || normalized.contains("junior grade")) {
            return "Junior";
        }
        if (normalized.equals("mid") || normalized.equals("middle") || normalized.contains("mid level") || normalized.contains("mid grade")) {
            return "Mid";
        }
        if (normalized.equals("intermediate") || normalized.contains("intermediate level") || normalized.contains("intermediate grade")) {
            return "Intermediate";
        }
        return null;
    }

    private List<String> cleanDesiredSkills(List<String> requiredSkills, List<String> desiredSkills, String statement) {
        if (!this.hasOptionalSkillLanguage(statement)) {
            return List.of();
        }
        return this.removeDuplicateSkills(requiredSkills, desiredSkills);
    }

    private List<String> reconciledRequiredSkills(ParsedRole role, String roleName, StatementSkillHints skillHints) {
        LinkedHashSet<String> optionalSkillKeys = this.skillKeys(skillHints.desiredSkills());
        LinkedHashSet<String> requiredHintKeys = this.skillKeys(skillHints.requiredSkills());
        ArrayList<String> candidates = new ArrayList<String>();
        for (String skill : this.cleanSkills(role.requiredSkills())) {
            if (optionalSkillKeys.contains(this.skillKey(skill))) continue;
            candidates.add(skill);
        }
        for (String skill : this.cleanSkills(role.desiredSkills())) {
            if (!requiredHintKeys.contains(this.skillKey(skill))) continue;
            candidates.add(skill);
        }
        for (String skill : skillHints.requiredSkills()) {
            if (!this.isSkillRelevantToRole(skill, roleName) || optionalSkillKeys.contains(this.skillKey(skill))) continue;
            candidates.add(skill);
        }
        return this.uniqueSkillsByKey(candidates);
    }

    private List<String> reconciledDesiredSkills(ParsedRole role, String roleName, StatementSkillHints skillHints, List<String> finalRequiredSkills) {
        LinkedHashSet<String> requiredSkillKeys = this.skillKeys(finalRequiredSkills);
        LinkedHashSet<String> optionalSkillKeys = this.skillKeys(skillHints.desiredSkills());
        ArrayList<String> candidates = new ArrayList<String>();
        if (!skillHints.desiredSkills().isEmpty()) {
            candidates.addAll(this.removeDuplicateSkills(role.requiredSkills(), role.desiredSkills()));
        }
        for (String skill2 : this.cleanSkills(role.requiredSkills())) {
            if (!optionalSkillKeys.contains(this.skillKey(skill2))) continue;
            candidates.add(skill2);
        }
        for (String skill2 : skillHints.desiredSkills()) {
            if (!this.isSkillRelevantToRole(skill2, roleName)) continue;
            candidates.add(skill2);
        }
        return this.uniqueSkillsByKey(candidates).stream().filter(skill -> !requiredSkillKeys.contains(this.skillKey((String)skill))).toList();
    }

    private StatementSkillHints extractStatementSkillHints(String statement) {
        return new StatementSkillHints(this.extractRequiredSkillHints(statement), this.extractDesiredSkillHints(statement));
    }

    private List<String> extractRequiredSkillHints(String statement) {
        ArrayList<String> skills = new ArrayList<String>();
        Matcher matcher = Pattern.compile("(?i)(?:required\\s+skills?(?:\\s+such\\s+as|\\s+include|\\s+including)?|must\\s+have\\s+skills?(?:\\s+such\\s+as|\\s+include|\\s+including)?)\\s+(.+?)(?:(?:\\.\\s)|;|\\b(?:optional|additional|preferred|nice-to-have|nice to have|desired|desirable)\\b|$)").matcher(Objects.toString(statement, ""));
        while (matcher.find()) {
            skills.addAll(this.splitSkillList(matcher.group(1)));
        }
        return this.uniqueSkillsByKey(skills);
    }

    private List<String> extractDesiredSkillHints(String statement) {
        String[] trailingMarkers;
        String source = Objects.toString(statement, "");
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        ArrayList<String> skills = new ArrayList<String>();
        for (String marker : trailingMarkers = new String[]{" are optional", " is optional", " are desired", " is desired", " are desirable", " is desirable", " are nice-to-have", " is nice-to-have", " are nice to have", " is nice to have"}) {
            int searchFrom = 0;
            int markerIndex = normalizedSource.indexOf(marker, searchFrom);
            while (markerIndex >= 0) {
                int start = Math.max(normalizedSource.lastIndexOf(". ", markerIndex), normalizedSource.lastIndexOf("; ", markerIndex));
                String segment = source.substring(start < 0 ? 0 : start + 2, markerIndex);
                skills.addAll(this.splitSkillList(segment));
                searchFrom = markerIndex + marker.length();
                markerIndex = normalizedSource.indexOf(marker, searchFrom);
            }
        }
        Matcher matcher = Pattern.compile("(?i)(?:optional|additional|desired|desirable|nice-to-have|nice to have)\\s+skills?(?:\\s+such\\s+as|\\s+include|\\s+including)?\\s+(.+?)(?:(?:\\.\\s)|;|$)").matcher(source);
        while (matcher.find()) {
            skills.addAll(this.splitSkillList(matcher.group(1)));
        }
        return this.uniqueSkillsByKey(skills);
    }

    private List<String> splitSkillList(String value) {
        ArrayList<String> skills = new ArrayList<String>();
        String cleaned = Objects.toString(value, "").replaceAll("(?i)^.*required\\s+skills?(?:\\s+such\\s+as|\\s+include|\\s+including)?", "").replaceAll("(?i)^.*(?:optional|additional|desired|desirable|nice-to-have|nice to have)\\s+skills?(?:\\s+such\\s+as|\\s+include|\\s+including)?", "").replaceAll("(?i)\\b(?:are|is)\\s+(?:optional|desired|desirable|nice-to-have|nice to have)\\b.*$", "").trim();
        for (String part : cleaned.split("(?i)\\s*,\\s*|\\s+and\\s+")) {
            String skill = part.trim().replaceAll("^[\\s:,-]+", "").replaceAll("(?i)^and\\s+", "").replaceAll("[\\s,;]+$", "");
            if (skill.endsWith(".")) {
                skill = skill.substring(0, skill.length() - 1);
            }
            if ((skill = this.canonicalSkillName(skill)).isBlank()) continue;
            skills.add(skill);
        }
        return skills;
    }

    private boolean isSkillRelevantToRole(String skill, String roleName) {
        String role = this.normalizeText(roleName);
        String key = this.skillKey(skill);
        if (role.contains("delivery") || role.contains("manager")) {
            return key.contains("risk management") || key.contains("stakeholder management") || key.contains("delivery management") || key.contains("agile") || key.contains("project management");
        }
        if (role.contains("product designer") || role.contains("designer") || role.contains("ux") || role.contains("ui")) {
            return key.contains("figma") || key.contains("product design") || key.contains("design systems") || key.contains("ux") || key.contains("ui");
        }
        if (role.contains("backend") || role.contains("back end")) {
            return key.contains("java") || key.contains("spring boot") || key.contains("node js") || key.contains("microservices") || key.contains("api") || key.contains("aws");
        }
        if (role.contains("frontend") || role.contains("front end")) {
            return key.contains("react") || key.contains("javascript") || key.contains("typescript") || key.contains("design systems") || key.contains("node js");
        }
        return false;
    }

    private LinkedHashSet<String> skillKeys(List<String> skills) {
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        for (String skill : this.defaultList(skills)) {
            String key = this.skillKey(skill);
            if (key.isBlank()) continue;
            keys.add(key);
        }
        return keys;
    }

    private String skillKey(String skill) {
        return this.normalizeText(skill).replaceAll("(?i)^and\\s+", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    private List<String> uniqueSkillsByKey(List<String> skills) {
        ArrayList<String> cleaned = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (String skill : this.defaultList(skills)) {
            String trimmed = skill == null ? null : skill.trim();
            String key = this.skillKey(trimmed);
            if (trimmed == null || trimmed.isBlank() || key.isBlank() || !seen.add(key)) continue;
            cleaned.add(this.canonicalSkillName(trimmed));
        }
        return cleaned;
    }

    private String canonicalSkillName(String skill) {
        String trimmed = Objects.toString(skill, "").trim().replaceAll("(?i)^and\\s+", "");
        Map<String, String> knownSkills = Map.ofEntries(Map.entry("risk management", "Risk management"), Map.entry("stakeholder management", "Stakeholder management"), Map.entry("react", "React"), Map.entry("javascript", "JavaScript"), Map.entry("java", "Java"), Map.entry("figma", "Figma"), Map.entry("product design", "Product design"), Map.entry("design systems", "Design systems"), Map.entry("spring boot", "Spring Boot"), Map.entry("node js", "Node.js"), Map.entry("typescript", "TypeScript"), Map.entry("aws", "AWS"), Map.entry("microservices", "Microservices"), Map.entry("api testing", "API Testing"), Map.entry("qa automation", "QA Automation"));
        return knownSkills.getOrDefault(this.skillKey(trimmed), trimmed);
    }

    private boolean hasOptionalSkillLanguage(String statement) {
        String value = this.normalizeText(statement);
        return value.contains("optional") || value.contains("additional") || value.contains("preferred") || value.contains("preferably") || value.contains("nice to have") || value.contains("nice-to-have") || value.contains("bonus") || value.contains("desirable") || value.contains("desired");
    }

    private List<String> removeDuplicateSkills(List<String> requiredSkills, List<String> desiredSkills) {
        List<String> required = this.cleanSkills(requiredSkills);
        LinkedHashSet<String> requiredKeys = new LinkedHashSet<String>();
        for (String skill : required) {
            requiredKeys.add(this.normalizeText(skill));
        }
        ArrayList<String> cleaned = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (String skill : this.defaultList(desiredSkills)) {
            String trimmed = skill == null ? null : skill.trim();
            String key = this.normalizeText(trimmed);
            if (trimmed == null || trimmed.isBlank() || requiredKeys.contains(key) || !seen.add(key)) continue;
            cleaned.add(trimmed);
        }
        return cleaned;
    }

    private List<String> cleanSkills(List<String> skills) {
        ArrayList<String> cleaned = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (String skill : this.defaultList(skills)) {
            String trimmed = skill == null ? null : skill.trim();
            String key = this.normalizeText(trimmed);
            if (trimmed == null || trimmed.isBlank() || !seen.add(key)) continue;
            cleaned.add(trimmed);
        }
        return cleaned;
    }

    private String cleanRolePriority(String parsedPriority, String fallbackPriority) {
        String normalized = this.normalizeText(parsedPriority);
        if (normalized.equals("high")) {
            return "High";
        }
        if (normalized.equals("medium") || normalized.equals("med")) {
            return "Medium";
        }
        if (normalized.equals("low")) {
            return "Low";
        }
        String fallback = this.normalizeText(fallbackPriority);
        if (fallback.equals("high")) {
            return "High";
        }
        if (fallback.equals("medium") || fallback.equals("med")) {
            return "Medium";
        }
        if (fallback.equals("low")) {
            return "Low";
        }
        return "Medium";
    }

    private String cleanCanCombineCandidates(String parsedValue, String roleName, String statement) {
        Boolean statementValue = this.statementCanCombineCandidatesForRole(roleName, statement);
        return Boolean.TRUE.equals(statementValue) ? "Yes" : "No";
    }

    private Boolean statementCanCombineCandidatesForRole(String roleName, String statement) {
        String normalizedRole = this.normalizeText(roleName);
        Boolean generalValue = null;
        for (String part : this.statementClauses(statement)) {
            String normalized = this.normalizeText(part);
            Boolean splitValue = this.candidateSplitValue(normalized);
            if (splitValue == null) {
                continue;
            }
            if (this.statementPartMentionsRole(normalized, normalizedRole)) {
                return splitValue;
            }
            if (normalized.contains("each role") || normalized.contains("all roles") || normalized.contains("every role")) {
                generalValue = splitValue;
            }
        }
        return generalValue;
    }

    private Boolean candidateSplitValue(String normalizedValue) {
        if (normalizedValue.contains("cannot combine") || normalizedValue.contains("can not combine") || normalizedValue.contains("do not combine") || normalizedValue.contains("must not combine") || normalizedValue.contains("cannot be split") || normalizedValue.contains("can not be split") || normalizedValue.contains("no split") || normalizedValue.contains("not split") || normalizedValue.contains("single candidate")) {
            return Boolean.FALSE;
        }
        if (normalizedValue.contains("can combine candidate") || normalizedValue.contains("combine candidates") || normalizedValue.contains("combined candidates") || normalizedValue.contains("can be split") || normalizedValue.contains("can split") || normalizedValue.contains("split across") || normalizedValue.contains("multiple candidates")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private boolean statementHasCandidateSplitForRole(String roleName, String statement) {
        return Boolean.TRUE.equals(this.statementCanCombineCandidatesForRole(roleName, statement));
    }

    private boolean statementPartMentionsRole(String normalizedStatementPart, String normalizedRole) {
        if (normalizedRole.isBlank()) {
            return false;
        }
        return normalizedStatementPart.contains(normalizedRole) || normalizedRole.contains("manager") && normalizedStatementPart.contains(normalizedRole.replace("manager", "manger"));
    }

    private boolean hasCandidateSplitLanguage(String normalizedValue) {
        return normalizedValue.contains("can combine candidate") || normalizedValue.contains("combine candidates") || normalizedValue.contains("combined candidates") || normalizedValue.contains("can be split") || normalizedValue.contains("can split") || normalizedValue.contains("split across") || normalizedValue.contains("multiple candidates");
    }

    private boolean statementMentionsAnyRole(String statement) {
        return !this.extractRoleNames(statement).isEmpty();
    }

    private String cleanLocationPreference(String locationPreference, Opportunity opportunity) {
        return this.cleanLocationPreference(locationPreference, opportunity, null);
    }

    private String cleanLocationPreference(String locationPreference, Opportunity opportunity, String statement) {
        String cleaned = this.cleanSpecificLocation(locationPreference);
        String statementLocation = this.cleanSpecificLocation(this.inferLocation(statement));
        String fallback = this.firstNonBlank(opportunity.getCountry(), opportunity.getCity(), "Unknown");

        if (statementLocation != null && !this.sameLocation(statementLocation, fallback) && (cleaned == null || this.isStructuredRequestLocation(cleaned, opportunity))) {
            cleaned = statementLocation;
        }

        if (cleaned != null && !fallback.equals("Unknown") && this.locationPreferenceIncludes(cleaned, fallback)) {
            return cleaned.trim();
        }

        if (cleaned != null && !this.sameLocation(cleaned, fallback) && !fallback.equals("Unknown")) {
            return fallback.trim() + " / " + cleaned.trim();
        }
        return this.firstNonBlank(fallback, cleaned, "Unknown");
    }

    private boolean sameLocation(String firstLocation, String secondLocation) {
        return this.normalizeText(firstLocation).equals(this.normalizeText(secondLocation));
    }

    private boolean isStructuredRequestLocation(String location, Opportunity opportunity) {
        return this.sameLocation(location, opportunity.getCountry()) || this.sameLocation(location, opportunity.getCity()) || this.sameLocation(location, opportunity.getRegion());
    }

    private boolean locationPreferenceIncludes(String locationPreference, String location) {
        String target = this.normalizeText(location);
        if (target.isBlank() || target.equals("unknown")) {
            return false;
        }
        for (String part : this.normalizeText(locationPreference).split("\\s*(?:/|,|;|\\bor\\b|\\band\\b)\\s*")) {
            if (part.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private String cleanSpecificLocation(String locationPreference) {
        String cleaned = this.firstNonBlank(locationPreference);
        if (cleaned == null) {
            return null;
        }
        String normalized = this.normalizeText(cleaned);
        if (normalized.equals("unknown") || normalized.equals("tbd") || normalized.equals("to be confirmed") || normalized.equals("not specified") || normalized.equals("none") || normalized.contains("no specific location") || normalized.contains("no location stated") || this.isFlexibilityOnlyLocation(normalized)) {
            return null;
        }
        if (normalized.equals("uk") || normalized.equals("united kingdom")) {
            return "UK";
        }
        return cleaned.trim();
    }

    private boolean isFlexibilityOnlyLocation(String normalizedValue) {
        return normalizedValue.contains("remote") || normalizedValue.contains("hybrid") || normalizedValue.contains("part time") || normalizedValue.contains("part-time") || normalizedValue.contains("available by start") || normalizedValue.contains("available by the start") || normalizedValue.contains("project start") || normalizedValue.contains("start date");
    }

    private String roleSpecificFlexibilityNotes(String roleName, String statement, String parsedNote) {
        String cleaned = this.cleanFlexibilityNotes(parsedNote);
        List<String> statementNotes = this.flexibilityNotesForRole(roleName, statement);
        LinkedHashSet<String> notes = new LinkedHashSet<String>();
        this.addParsedFlexibilityNotesForRole(notes, roleName, statement, cleaned);
        for (String statementNote : statementNotes) {
            this.addFlexibilityNote(notes, statementNote);
        }
        return notes.isEmpty() ? "Confirm with requester" : String.join((CharSequence)"; ", notes);
    }

    private List<String> flexibilityNotesForRole(String roleName, String statement) {
        ArrayList<String> notes = new ArrayList<String>();
        String normalizedRole = this.normalizeText(roleName);
        if (normalizedRole.isBlank()) {
            return notes;
        }
        for (String part : Objects.toString(statement, "").split("[.;]")) {
            String trimmed = part.trim();
            String normalized = this.normalizeText(trimmed);
            int roleIndex = normalized.indexOf(normalizedRole);
            if (roleIndex < 0 || !this.isRoleFlexibilityNote(normalized)) {
                continue;
            }
            String note = trimmed;
            if (this.isAvailabilityNote(normalized)) {
                String afterRole = trimmed.substring(Math.min(trimmed.length(), roleIndex + roleName.length())).trim();
                if (!afterRole.isBlank()) {
                    note = afterRole;
                }
            }
            this.addFlexibilityNote(notes, note);
        }
        return notes;
    }

    private void addFlexibilityNote(Collection<String> notes, String note) {
        String cleaned = this.canonicalFlexibilityNote(note);
        if (cleaned != null && !cleaned.equals("Confirm with requester")) {
            notes.add(cleaned);
        }
    }

    private void addParsedFlexibilityNotesForRole(Collection<String> notes, String roleName, String statement, String cleanedNotes) {
        for (String part : Objects.toString(cleanedNotes, "").split(";")) {
            String cleaned = this.canonicalFlexibilityNote(part);
            if (cleaned == null || cleaned.equals("Confirm with requester")) {
                continue;
            }
            String normalized = this.normalizeText(cleaned);
            if (this.isWorkingModeNote(normalized) && !this.statementHasRoleWorkingModeNote(roleName, statement, normalized)) {
                continue;
            }
            notes.add(cleaned);
        }
    }

    private boolean isWorkingModeNote(String normalizedValue) {
        return normalizedValue.contains("remote") || normalizedValue.contains("hybrid") || normalizedValue.contains("part time") || normalizedValue.contains("part-time");
    }

    private boolean statementHasRoleWorkingModeNote(String roleName, String statement, String normalizedNote) {
        String normalizedRole = this.normalizeText(roleName);
        if (normalizedRole.isBlank()) {
            return false;
        }
        for (String part : Objects.toString(statement, "").split("[.;]")) {
            String normalized = this.normalizeText(part);
            if (normalized.indexOf(normalizedRole) < 0) {
                continue;
            }
            if (normalizedNote.contains("remote") && normalized.contains("remote")) {
                return true;
            }
            if (normalizedNote.contains("hybrid") && normalized.contains("hybrid")) {
                return true;
            }
            if ((normalizedNote.contains("part time") || normalizedNote.contains("part-time")) && (normalized.contains("part time") || normalized.contains("part-time"))) {
                return true;
            }
        }
        return false;
    }

    private boolean isRoleFlexibilityNote(String normalizedValue) {
        return normalizedValue.contains("remote") || normalizedValue.contains("hybrid") || normalizedValue.contains("part time") || normalizedValue.contains("part-time") || this.isAvailabilityNote(normalizedValue);
    }

    private boolean isAvailabilityNote(String normalizedValue) {
        return normalizedValue.contains("available") && (normalizedValue.contains("project start") || normalizedValue.contains("project starts") || normalizedValue.contains("start date"));
    }

    private String cleanFlexibilityNotes(String note) {
        LinkedHashSet<String> kept = new LinkedHashSet<String>();
        for (String part : Objects.toString(note, "").split("[.;]")) {
            String cleaned = this.canonicalFlexibilityNote(part);
            if (cleaned != null && !this.isParsedAvailabilityNote(cleaned)) {
                kept.add(cleaned);
            }
        }
        return kept.isEmpty() ? "Confirm with requester" : String.join((CharSequence)"; ", kept);
    }

    private String canonicalFlexibilityNote(String note) {
        String trimmed = Objects.toString(note, "").trim();
        String normalized = this.normalizeText(trimmed);
        if (normalized.isBlank() || normalized.matches("\\d+(?:\\.\\d+)?") || normalized.contains("senior level preferred") || normalized.contains("can combine candidates") || normalized.contains("combined candidates") || normalized.contains("staffed by combined") || normalized.contains("can be split") || normalized.contains("can split") || normalized.contains("split across") || normalized.contains("multiple candidates") || normalized.contains("minimum individual allocation") || normalized.contains("minimum individual fte") || normalized.contains("no specific location") || normalized.contains("no location stated")) {
            return null;
        }
        if (normalized.contains("remote")) {
            return "Remote allowed";
        }
        if (normalized.contains("hybrid")) {
            return "Hybrid allowed";
        }
        if (normalized.contains("part time") || normalized.contains("part-time")) {
            return "Part-time allowed";
        }
        trimmed = trimmed.replaceFirst("(?i)^must\\s+be\\s+", "must ").trim();
        if (trimmed.startsWith("must ")) {
            trimmed = "Must " + trimmed.substring(5);
        }
        if (!trimmed.endsWith(".")) {
            trimmed = trimmed + ".";
        }
        return trimmed;
    }

    private boolean isParsedAvailabilityNote(String note) {
        return this.isAvailabilityNote(this.normalizeText(note));
    }


    private String normalizeText(String value) {
        return Objects.toString(value, "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }


    private String cleanRoleDomain(String parsedDomain, Opportunity opportunity) {
        String cleaned = this.cleanDomain(parsedDomain);
        if (this.isKnownDomain(cleaned)) {
            return cleaned;
        }
        String fallback = this.cleanDomain(opportunity == null ? null : opportunity.getDomain());
        return this.isKnownDomain(fallback) ? fallback : null;
    }

    private boolean isKnownDomain(String value) {
        return value != null && this.knownDomains().containsValue(value);
    }
    private String cleanDomain(String value) {
        String cleaned = this.firstNonBlank(value);
        if (cleaned == null) {
            return null;
        }
        cleaned = cleaned.trim()
                .replaceAll("\\.+$", "")
                .replaceAll("(?i)\\s+domain\\s+experience\\s+(?:required|preferred|needed)\\s*$", "")
                .replaceAll("(?i)\\s+domain\\s+experience\\s*$", "")
                .replaceAll("(?i)\\s+experience\\s+(?:required|preferred|needed)\\s*$", "")
                .replaceAll("(?i)\\s+(?:required|preferred|needed)\\s*$", "")
                .replaceAll("(?i)\\s+domain\\s+(?:required|preferred|needed)\\s*$", "")
                .replaceAll("(?i)\\s+domain\\s*$", "")
                .trim();
        return cleaned.isBlank() ? null : this.canonicalDomain(cleaned);
    }

    private Map<String, String> knownDomains() {
        return Map.ofEntries(Map.entry("banking", "Banking"), Map.entry("education", "Education"), Map.entry("energy", "Energy"), Map.entry("financial services", "Financial Services"), Map.entry("healthcare", "Healthcare"), Map.entry("insurance", "Insurance"), Map.entry("internal platforms", "Internal Platforms"), Map.entry("legacy platforms", "Legacy Platforms"), Map.entry("logistics", "Logistics"), Map.entry("media", "Media"), Map.entry("payments", "Payments"), Map.entry("public sector", "Public Sector"), Map.entry("retail", "Retail"), Map.entry("telecommunications", "Telecommunications"), Map.entry("travel", "Travel"));
    }

    private String canonicalDomain(String value) {
        String normalized = this.normalizeText(value);
        Map<String, String> knownDomains = this.knownDomains();
        String exactMatch = knownDomains.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }
        for (Map.Entry<String, String> entry : knownDomains.entrySet()) {
            if (normalized.startsWith(entry.getKey() + " ") || normalized.contains(entry.getKey() + " domain") || normalized.contains(entry.getKey() + " experience")) {
                return entry.getValue();
            }
        }
        return value.trim().replaceAll("\\.+$", "");
    }

    private String inferDomain(String statement) {
        String value = Objects.toString(statement, "").toLowerCase(Locale.ROOT);
        if (value.contains("bank") || value.contains("payment") || value.contains("financial")) {
            return "Banking";
        }
        if (value.contains("insurance")) {
            return "Insurance";
        }
        if (value.contains("retail") || value.contains("commerce")) {
            return "Retail";
        }
        if (value.contains("health")) {
            return "Healthcare";
        }
        return "Unknown";
    }

    private String inferLocation(String statement) {
        String value = this.normalizeText(statement);
        if (Pattern.compile("\\b(?:uk|london|united kingdom)\\b").matcher(value).find()) {
            return "UK";
        }
        if (value.contains("india") || value.contains("pune") || value.contains("bengaluru")) {
            return "India";
        }
        if (value.contains("mena") || value.contains("dubai") || value.contains("uae")) {
            return "MENA";
        }
        if (value.contains("malaysia") || value.contains("kuala lumpur")) {
            return "Malaysia";
        }
        return "Unknown";
    }

    private List<String> inferSkills(String statement) {
        List<String> knownSkills = List.of("React", "TypeScript", "Java", "Spring Boot", "AWS", "Microservices", "QA Automation", "API Testing", "Cypress", "Playwright", "Agile Delivery", "Stakeholder Management");
        String normalized = Objects.toString(statement, "").toLowerCase(Locale.ROOT);
        List<String> matches = knownSkills.stream().filter(skill -> normalized.contains(skill.toLowerCase(Locale.ROOT))).toList();
        return matches.isEmpty() ? List.of() : matches;
    }

    private int inferTeamSize(String statement) {
        Matcher matcher = Pattern.compile("(?:need|for)\\s+(\\d+)\\s+(?:people|roles|engineers|person)", 2).matcher(Objects.toString(statement, ""));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    private boolean hasOpenAiKey() {
        return this.openAiApiKey != null && !this.openAiApiKey.isBlank();
    }

    private String firstNonBlank(String ... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            return value;
        }
        return null;
    }

    private Integer defaultInteger(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private BigDecimal defaultDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private String[] statementClauses(String statement) {
        return Objects.toString(statement, "").split(";|(?<!\\d)\\.(?!\\d)");
    }

    private BigDecimal cleanFteRequired(String roleName, BigDecimal parsedValue, String statement) {
        BigDecimal roleSpecificValue = this.statementFteForRole(roleName, statement);
        if (roleSpecificValue != null) {
            return roleSpecificValue;
        }
        BigDecimal generalValue = this.statementDefaultFte(statement);
        return generalValue == null ? BigDecimal.ONE : generalValue;
    }

    private BigDecimal statementFteForRole(String roleName, String statement) {
        String normalizedRole = this.normalizeText(roleName);
        if (normalizedRole.isBlank()) {
            return null;
        }
        for (String part : this.statementClauses(statement)) {
            String normalized = this.normalizeText(part);
            if (!this.statementPartMentionsRole(normalized, normalizedRole)) {
                continue;
            }
            BigDecimal value = this.extractFteValue(part, true);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal statementDefaultFte(String statement) {
        for (String part : this.statementClauses(statement)) {
            String normalized = this.normalizeText(part);
            if (!normalized.contains("fte") || !(normalized.contains("each") || normalized.contains("all roles") || normalized.contains("every role"))) {
                continue;
            }
            BigDecimal value = this.extractFteValue(part, false);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal statementMinimumIndividualFteForRole(String roleName, String statement) {
        String normalizedRole = this.normalizeText(roleName);
        if (normalizedRole.isBlank()) {
            return null;
        }
        for (String part : this.statementClauses(statement)) {
            String normalized = this.normalizeText(part);
            if (!this.statementPartMentionsRole(normalized, normalizedRole)) {
                continue;
            }
            Matcher matcher = Pattern.compile("\\b(?:min(?:imum)?\\s+)?individual\\s+fte\\s*(?:is|=|:)?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(part);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        }
        return null;
    }

    private BigDecimal extractFteValue(String text, boolean preferFteAfterLabel) {
        if (preferFteAfterLabel) {
            Matcher matcher = Pattern.compile("(?<!individual\\s)\\bfte\\s*(?:is|=|:)?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*fte\\b", Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        if (!preferFteAfterLabel) {
            matcher = Pattern.compile("\\bfte\\s*(?:is|=|:)?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(text);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        }
        return null;
    }

    private BigDecimal defaultFteRequired(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : value;
    }

    private List<String> defaultList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private record ParsedResult(ParsedOpportunity opportunity, String source) {
    }

    public record ParsedOpportunity(String opportunityName, String domain, String projectType, String deliveryRisk, List<ParsedRole> roles, List<String> validationNotes) {
        public ParsedOpportunity {
            roles = roles == null ? List.of() : roles;
            validationNotes = validationNotes == null ? new ArrayList<String>() : new ArrayList<String>(validationNotes);
        }
    }

    public record ParsedRole(String roleName, String disciplineOrDepartment, String gradePreference, List<String> requiredSkills, List<String> desiredSkills, String domainExperienceRequired, String locationPreference, Integer durationWeeks, BigDecimal fteRequired, String priority, String flexibilityNotes, BigDecimal minimumIndividualFTE, String canCombineCandidates) {
        public ParsedRole {
            requiredSkills = requiredSkills == null ? List.of() : requiredSkills;
            desiredSkills = desiredSkills == null ? List.of() : desiredSkills;
        }
    }

    private record StatementSkillHints(List<String> requiredSkills, List<String> desiredSkills) {
    }
}
