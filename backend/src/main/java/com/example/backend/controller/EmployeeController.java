package com.example.backend.controller;

import com.example.backend.entity.Bench;
import com.example.backend.entity.Allocation;
import com.example.backend.entity.Availability;
import com.example.backend.entity.Employee;
import com.example.backend.entity.EmployeeSkill;
import com.example.backend.entity.Profile;
import com.example.backend.entity.ProjectHistory;
import com.example.backend.repository.AllocationRepository;
import com.example.backend.repository.AvailabilityRepository;
import com.example.backend.repository.BenchRepository;
import com.example.backend.repository.EmployeeRepository;
import com.example.backend.repository.EmployeeSkillRepository;
import com.example.backend.repository.ProfileRepository;
import com.example.backend.repository.ProjectHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final BenchRepository benchRepository;
    private final ProfileRepository profileRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final AllocationRepository allocationRepository;
    private final AvailabilityRepository availabilityRepository;

    public EmployeeController(
            EmployeeRepository employeeRepository,
            EmployeeSkillRepository employeeSkillRepository,
            BenchRepository benchRepository,
            ProfileRepository profileRepository,
            ProjectHistoryRepository projectHistoryRepository,
            AllocationRepository allocationRepository,
            AvailabilityRepository availabilityRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.benchRepository = benchRepository;
        this.profileRepository = profileRepository;
        this.projectHistoryRepository = projectHistoryRepository;
        this.allocationRepository = allocationRepository;
        this.availabilityRepository = availabilityRepository;
    }

    @GetMapping
    public EmployeePageResponse listEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String skillSearch,
            @RequestParam(defaultValue = "all") String availability,
            @RequestParam(defaultValue = "all") String region,
            @RequestParam(defaultValue = "all") String grade,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "all") String role,
            @RequestParam(defaultValue = "all") String domain
    ) {
        int pageSize = Math.max(1, Math.min(size, 100));
        int pageIndex = Math.max(page, 0);
        List<Employee> allEmployees = employeeRepository.findAll(Sort.by("employeeName").ascending());
        List<String> allEmployeeIds = allEmployees.stream()
                .map(Employee::getEmployeeId)
                .filter(Objects::nonNull)
                .toList();

        Map<String, List<EmployeeSkill>> skillsByEmployee = employeeSkillRepository.findByEmployeeIdIn(allEmployeeIds)
                .stream()
                .collect(Collectors.groupingBy(EmployeeSkill::getEmployeeId));
        Map<String, Bench> benchByEmployee = benchRepository.findByEmployeeIdIn(allEmployeeIds)
                .stream()
                .collect(Collectors.toMap(
                        Bench::getEmployeeId,
                        bench -> bench,
                        (first, ignored) -> first
                ));

        List<Employee> filteredEmployees = allEmployees.stream()
                .filter(employee -> matchesSkillSearch(
                        skillsByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        skillSearch
                ))
                .filter(employee -> matchesAvailability(employee, availability))
                .filter(employee -> matchesRegion(employee, region))
                .filter(employee -> matchesGrade(employee, grade))
                .filter(employee -> matchesStatus(employee, benchByEmployee.containsKey(employee.getEmployeeId()), status))
                .toList();

        List<TalentExplorerEmployee> sortedEmployees = filteredEmployees.stream()
                .map(employee -> toTalentEmployee(
                        employee,
                        skillsByEmployee.getOrDefault(employee.getEmployeeId(), List.of()),
                        benchByEmployee.get(employee.getEmployeeId()),
                        skillSearch
                ))
                .sorted(Comparator
                        .comparingInt(TalentExplorerEmployee::fitScore)
                        .reversed()
                        .thenComparing(TalentExplorerEmployee::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        int totalElements = sortedEmployees.size();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        int fromIndex = Math.min(pageIndex * pageSize, totalElements);
        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        List<TalentExplorerEmployee> employees = sortedEmployees.subList(fromIndex, toIndex);

        return new EmployeePageResponse(
                employees,
                pageIndex,
                pageSize,
                totalElements,
                totalPages
        );
    }

    @GetMapping("/{employeeId}")
    public Employee getEmployee(@PathVariable String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));
    }

    @GetMapping("/filter-options")
    public EmployeeFilterOptions getFilterOptions() {
        List<Employee> employees = employeeRepository.findAll();
        Set<String> grades = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> regions = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (Employee employee : employees) {
            addIfPresent(grades, employee.getGrade());
            addIfPresent(regions, employee.getRegion());
        }

        return new EmployeeFilterOptions(new ArrayList<>(grades), new ArrayList<>(regions));
    }

    @GetMapping("/{employeeId}/profile")
    public PersonProfileResponse getPersonProfile(@PathVariable String employeeId) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
        List<EmployeeSkill> skills = employeeSkillRepository.findByEmployeeId(employeeId);
        Bench bench = benchRepository.findByEmployeeId(employeeId).stream().findFirst().orElse(null);
        Profile profile = profileRepository.findByEmployeeId(employeeId).orElse(null);
        List<ProjectHistory> projectHistory = projectHistoryRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
        List<Allocation> allocations = allocationRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);
        List<Availability> availability = availabilityRepository.findByEmployeeIdOrderByWeekStartDateAsc(employeeId);

        return new PersonProfileResponse(
                profileSummary(employee, bench, allocations),
                profileSkills(skills),
                profileDomains(employee, profile, projectHistory, allocations),
                profileDetails(profile),
                profileAllocations(allocations),
                projectEvidence(projectHistory),
                availabilityForecast(availability)
        );
    }

    private PersonProfileSummary profileSummary(
            Employee employee,
            Bench bench,
            List<Allocation> allocations
    ) {
        Allocation currentAllocation = allocations.stream().findFirst().orElse(null);
        LocalDate currentProjectEnd = firstDate(
                employee.getCurrentProjectEnd(),
                currentAllocation == null ? null : currentAllocation.getPlannedEndDate(),
                bench == null ? null : bench.getAvailableFrom(),
                employee.getExpectedReleaseDate()
        );

        return new PersonProfileSummary(
                employee.getEmployeeId(),
                initials(employee.getEmployeeName()),
                employee.getEmployeeName(),
                employee.getGrade(),
                firstNonBlank(employee.getRoleArchetype(), employee.getCurrentRole(), employee.getDiscipline()),
                firstNonBlank(employee.getCity(), employee.getCountry(), employee.getRegion()),
                employee.getRegion(),
                employee.getCountry(),
                employee.getCity(),
                employee.getDepartment(),
                employee.getDiscipline(),
                availabilityText(employee),
                employee.getAvailabilityCategory(),
                firstNonBlank(
                        currentAllocation == null ? null : currentAllocation.getProjectName(),
                        employee.getCurrentProjectId()
                ),
                currentProjectEnd,
                employee.getEwaStatus(),
                employee.getWorkMode(),
                employee.getCurrentAllocationFTE(),
                employee.getAvailableFTECurrent()
        );
    }

    private List<PersonProfileSkill> profileSkills(List<EmployeeSkill> employeeSkills) {
        return employeeSkills.stream()
                .sorted(Comparator
                        .comparing(EmployeeSkill::getSkillLevel, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EmployeeSkill::getYearsExperience, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EmployeeSkill::getSkillName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(skill -> new PersonProfileSkill(
                        skill.getSkillName(),
                        skill.getSkillCategory(),
                        skill.getSkillLevel(),
                        skill.getYearsExperience(),
                        skill.getLastUsedDate(),
                        skill.getEvidenceSource(),
                        skill.getConfidence()
                ))
                .toList();
    }

    private List<String> profileDomains(
            Employee employee,
            Profile profile,
            List<ProjectHistory> projectHistory,
            List<Allocation> allocations
    ) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        addIfPresent(domains, employee.getPrimaryDomain());
        addIfPresent(domains, employee.getSecondaryDomain());
        if (profile != null && profile.getDomainExperienceSummary() != null) {
            profile.getDomainExperienceSummary().values().forEach(value -> addIfPresent(domains, value));
        }
        projectHistory.forEach(project -> addIfPresent(domains, project.getDomain()));
        allocations.forEach(allocation -> addIfPresent(domains, allocation.getDomain()));
        return new ArrayList<>(domains);
    }

    private PersonProfileDetails profileDetails(Profile profile) {
        if (profile == null) {
            return new PersonProfileDetails("", List.of(), List.of(), Map.of(), List.of(), List.of(), "", List.of());
        }

        return new PersonProfileDetails(
                profile.getProfileSummary(),
                safeList(profile.getKeyStrengths()),
                safeList(profile.getPreferredWorkTypes()),
                profile.getDomainExperienceSummary() == null ? Map.of() : profile.getDomainExperienceSummary(),
                safeList(profile.getCertifications()),
                safeList(profile.getRecentHighlights()),
                profile.getMobilityNotes(),
                safeList(profile.getLanguages())
        );
    }

    private List<PersonAllocation> profileAllocations(List<Allocation> allocations) {
        return allocations.stream()
                .map(allocation -> new PersonAllocation(
                        allocation.getProjectName(),
                        allocation.getClientName(),
                        allocation.getClientType(),
                        allocation.getDomain(),
                        allocation.getRoleOnProject(),
                        allocation.getAllocationFTE(),
                        allocation.getStartDate(),
                        allocation.getPlannedEndDate(),
                        allocation.getAllocationStatus(),
                        allocation.getEwaStatus()
                ))
                .toList();
    }

    private List<PersonProjectEvidence> projectEvidence(List<ProjectHistory> projectHistory) {
        return projectHistory.stream()
                .map(project -> new PersonProjectEvidence(
                        project.getProjectName(),
                        project.getClientName(),
                        project.getClientType(),
                        project.getDomain(),
                        project.getRole(),
                        project.getStartDate(),
                        project.getEndDate(),
                        safeList(project.getKeyTechnologiesOrMethods()),
                        safeList(project.getResponsibilities()),
                        safeList(project.getOutcomeEvidence()),
                        project.getRegion(),
                        project.getTeamSize()
                ))
                .toList();
    }

    private List<PersonAvailability> availabilityForecast(List<Availability> availability) {
        return availability.stream()
                .map(item -> new PersonAvailability(
                        item.getWeekStartDate(),
                        item.getAvailableFTE(),
                        item.getAvailabilityType(),
                        item.getSource(),
                        item.getConfidence(),
                        item.getEwaStatus(),
                        item.getNotes()
                ))
                .toList();
    }

    private boolean matchesSkillSearch(List<EmployeeSkill> employeeSkills, String skillSearch) {
        List<String> searchTerms = skillSearchTerms(skillSearch);
        if (searchTerms.isEmpty()) {
            return true;
        }
        List<String> candidateSkills = employeeSkills.stream()
                .map(EmployeeSkill::getSkillName)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .toList();

        return !candidateSkills.isEmpty()
                && searchTerms.stream()
                .allMatch(term -> candidateSkills.stream().anyMatch(skillName -> skillName.contains(term)));
    }

    private boolean matchesAvailability(Employee employee, String availability) {
        String normalizedAvailability = normalize(availability);
        if (normalizedAvailability.isBlank() || "all".equals(normalizedAvailability)) {
            return true;
        }

        int days;
        try {
            days = Integer.parseInt(normalizedAvailability);
        } catch (NumberFormatException ignored) {
            return true;
        }

        LocalDate releaseDate = employee.getExpectedReleaseDate();
        if (releaseDate != null) {
            return !releaseDate.isAfter(LocalDate.now().plusDays(days));
        }

        BigDecimal availableFte = employee.getAvailableFTECurrent();
        return availableFte != null && availableFte.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean matchesRegion(Employee employee, String region) {
        return matchesAnyText(region, employee.getRegion());
    }

    private boolean matchesGrade(Employee employee, String grade) {
        return matchesAnyText(grade, employee.getGrade());
    }

    private boolean matchesStatus(Employee employee, boolean hasBenchRecord, String status) {
        String normalizedStatus = normalize(status);
        if (normalizedStatus.isBlank() || "all".equals(normalizedStatus)) {
            return true;
        }

        boolean isBench = hasBenchRecord || containsAny(employee.getAvailabilityCategory(), Set.of("bench"));
        boolean isRollOff = employee.getExpectedReleaseDate() != null
                || employee.getReleaseWindow() != null
                || containsAny(employee.getAvailabilityCategory(), Set.of("roll", "release"));

        return switch (normalizedStatus) {
            case "bench" -> isBench;
            case "rolloff" -> isRollOff;
            case "benchrolloff" -> isBench || isRollOff;
            default -> true;
        };
    }

    private boolean matchesAnyText(String filter, String... values) {
        String normalizedFilter = normalize(filter);
        if (normalizedFilter.isBlank() || "all".equals(normalizedFilter) || "any".equals(normalizedFilter)) {
            return true;
        }
        return List.of(values).stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .anyMatch(value -> value.equals(normalizedFilter) || value.contains(normalizedFilter));
    }

    private boolean containsAny(String value, Set<String> fragments) {
        String normalizedValue = normalize(value);
        return fragments.stream().anyMatch(normalizedValue::contains);
    }

    private TalentExplorerEmployee toTalentEmployee(
            Employee employee,
            List<EmployeeSkill> employeeSkills,
            Bench bench,
            String skillSearch
    ) {
        List<String> skills = employeeSkills.stream()
                .sorted(Comparator
                        .comparing(EmployeeSkill::getSkillLevel, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EmployeeSkill::getYearsExperience, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(EmployeeSkill::getSkillName)
                .filter(Objects::nonNull)
                .distinct()
                .limit(3)
                .toList();

        if (skills.isEmpty() && bench != null && bench.getTopSkills() != null) {
            skills = bench.getTopSkills().stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(3)
                    .toList();
        }

        String domain = List.of(employee.getPrimaryDomain(), employee.getSecondaryDomain()).stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        int fitScore = fitScore(
                employee,
                employeeSkills,
                bench,
                skillSearch
        );

        return new TalentExplorerEmployee(
                employee.getEmployeeId(),
                initials(employee.getEmployeeName()),
                employee.getEmployeeName(),
                employee.getGrade(),
                firstNonBlank(employee.getRoleArchetype(), employee.getCurrentRole(), employee.getDiscipline()),
                skills,
                firstNonBlank(employee.getCity(), employee.getCountry(), employee.getRegion()),
                availabilityText(employee),
                employee.getExpectedReleaseDate(),
                domain,
                employee.getDepartment(),
                fitScore
        );
    }

    private int fitScore(
            Employee employee,
            List<EmployeeSkill> employeeSkills,
            Bench bench,
            String skillSearch
    ) {
        double skillScore = skillScore(employeeSkills, skillSearch);
        double availabilityScore = availabilityDaysScore(daysUntilAvailable(employee, bench));

        double weightedScore = skillScore * 0.80
                + availabilityScore * 0.20;

        return (int) Math.round(weightedScore);
    }

    private double skillScore(List<EmployeeSkill> employeeSkills, String skillSearch) {
        if (normalize(skillSearch).isBlank()) {
            return 100;
        }

        List<String> searchTerms = skillSearchTerms(skillSearch);
        if (searchTerms.isEmpty()) {
            return 100;
        }

        List<String> candidateSkills = employeeSkills.stream()
                .map(EmployeeSkill::getSkillName)
                .filter(Objects::nonNull)
                .map(this::normalize)
                .toList();

        if (candidateSkills.isEmpty()) {
            return 0;
        }

        double totalScore = 0;
        for (String term : searchTerms) {
            totalScore += candidateSkills.stream()
                    .mapToDouble(skill -> singleTextScore(term, skill))
                    .max()
                    .orElse(0);
        }

        return totalScore / searchTerms.size();
    }

    private double combinedAvailabilityScore(Employee employee, Bench bench, String status) {
        return (availabilityDaysScore(daysUntilAvailable(employee, bench)) + statusScore(employee, bench, status)) / 2;
    }

    private double availabilityDaysScore(long days) {
        if (days <= 30) {
            return 100;
        }
        if (days <= 60) {
            return 85;
        }
        if (days <= 90) {
            return 70;
        }
        if (days <= 120) {
            return 50;
        }
        if (days <= 150) {
            return 30;
        }
        return 10;
    }

    private long daysUntilAvailable(Employee employee, Bench bench) {
        LocalDate releaseDate = firstDate(employee.getExpectedReleaseDate(), bench == null ? null : bench.getAvailableFrom());
        if (releaseDate != null) {
            return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), releaseDate));
        }

        BigDecimal availableFte = employee.getAvailableFTECurrent();
        if (availableFte != null && availableFte.compareTo(BigDecimal.ZERO) > 0) {
            return 0;
        }

        return 151;
    }

    private double statusScore(Employee employee, Bench bench, String status) {
        String explicitStatus = normalize(status);
        if (!explicitStatus.isBlank() && !"all".equals(explicitStatus)) {
            return switch (explicitStatus) {
                case "bench" -> isBenchOrAvailable(employee, bench) ? 100 : 0;
                case "rolloff" -> isRollingOff(employee) ? 85 : 0;
                case "benchrolloff" -> isBenchOrAvailable(employee, bench) || isRollingOff(employee) ? 100 : 0;
                default -> staffingStatusScore(employee, bench);
            };
        }

        return staffingStatusScore(employee, bench);
    }

    private double staffingStatusScore(Employee employee, Bench bench) {
        if (containsAny(employee.getAvailabilityCategory(), Set.of("blocked", "inactive"))) {
            return 0;
        }
        if (isBenchOrAvailable(employee, bench)) {
            return 100;
        }
        if (isRollingOff(employee)) {
            return 85;
        }

        BigDecimal currentAllocation = employee.getCurrentAllocationFTE();
        BigDecimal availableFte = employee.getAvailableFTECurrent();
        if (currentAllocation != null) {
            if (currentAllocation.compareTo(BigDecimal.ONE) >= 0) {
                return 20;
            }
            if (currentAllocation.compareTo(BigDecimal.ZERO) > 0) {
                return 60;
            }
        }
        if (availableFte != null && availableFte.compareTo(BigDecimal.ZERO) > 0) {
            return 100;
        }

        return 50;
    }

    private boolean isBenchOrAvailable(Employee employee, Bench bench) {
        BigDecimal availableFte = employee.getAvailableFTECurrent();
        return bench != null
                || containsAny(employee.getAvailabilityCategory(), Set.of("bench", "available"))
                || (availableFte != null && availableFte.compareTo(BigDecimal.ZERO) > 0);
    }

    private boolean isRollingOff(Employee employee) {
        return employee.getExpectedReleaseDate() != null
                || employee.getReleaseWindow() != null
                || containsAny(employee.getAvailabilityCategory(), Set.of("roll", "release"));
    }

    private double locationScore(Employee employee, String location) {
        String normalizedLocation = normalize(location);
        if (normalizedLocation.isBlank() || "all".equals(normalizedLocation) || "any".equals(normalizedLocation)) {
            return 100;
        }

        String city = normalize(employee.getCity());
        String region = normalize(employee.getRegion());
        String country = normalize(employee.getCountry());
        String workMode = normalize(employee.getWorkMode());

        if (!city.isBlank() && (city.equals(normalizedLocation) || normalizedLocation.contains(city))) {
            return 100;
        }
        if (!region.isBlank() && (region.equals(normalizedLocation) || normalizedLocation.contains(region))) {
            return 80;
        }
        if (workMode.contains("remote") || workMode.contains("global")) {
            return 70;
        }
        if (!country.isBlank() && (country.equals(normalizedLocation) || normalizedLocation.contains(country))) {
            return 50;
        }
        return 0;
    }

    private double gradeScore(String targetGrade, String candidateGrade) {
        String normalizedTargetGrade = normalize(targetGrade);
        if (
                normalizedTargetGrade.isBlank()
                        || "all".equals(normalizedTargetGrade)
                        || "any".equals(normalizedTargetGrade)
        ) {
            return 100;
        }

        int targetIndex = gradeIndex(normalizedTargetGrade);
        int candidateIndex = gradeIndex(normalize(candidateGrade));
        if (targetIndex < 0 || candidateIndex < 0) {
            return singleTextScore(normalizedTargetGrade, normalize(candidateGrade));
        }

        int distance = Math.abs(targetIndex - candidateIndex);
        if (distance == 0) {
            return 100;
        }
        if (distance == 1) {
            return 80;
        }
        if (distance == 2) {
            return 50;
        }
        return 20;
    }

    private int gradeIndex(String normalizedGrade) {
        return List.of(
                "consultant",
                "seniorconsultant",
                "leadconsultant",
                "manager",
                "seniormanager",
                "director"
        ).indexOf(normalizedGrade);
    }

    private double textScore(String target, String... candidateValues) {
        String normalizedTarget = normalize(target);
        if (
                normalizedTarget.isBlank()
                        || "all".equals(normalizedTarget)
                        || "any".equals(normalizedTarget)
        ) {
            return 100;
        }

        return List.of(candidateValues).stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .mapToDouble(value -> singleTextScore(normalizedTarget, value))
                .max()
                .orElse(0);
    }

    private double singleTextScore(String normalizedTarget, String normalizedCandidate) {
        if (normalizedCandidate.equals(normalizedTarget)) {
            return 100;
        }
        if (normalizedCandidate.contains(normalizedTarget)) {
            return 90;
        }
        if (normalizedTarget.contains(normalizedCandidate)) {
            return 75;
        }
        return 0;
    }

    private String availabilityText(Employee employee) {
        LocalDate releaseDate = employee.getExpectedReleaseDate();
        String category = employee.getAvailabilityCategory();

        if (releaseDate == null) {
            return firstNonBlank(category, "Availability unknown");
        }

        long days = ChronoUnit.DAYS.between(LocalDate.now(), releaseDate);
        if (days <= 0) {
            return "Available now";
        }
        if (days == 1) {
            return "Available in 1 day";
        }
        return "Available in " + days + " days";
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) {
            return "NA";
        }
        return List.of(name.trim().split("\\s+")).stream()
                .filter(part -> !part.isBlank())
                .limit(2)
                .map(part -> part.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private LocalDate firstDate(LocalDate... dates) {
        for (LocalDate date : dates) {
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private List<String> skillSearchTerms(String value) {
        if (value == null) {
            return List.of();
        }
        return List.of(value.split(",")).stream()
                .map(this::normalize)
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
    }

    public record EmployeePageResponse(
            List<TalentExplorerEmployee> employees,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record EmployeeFilterOptions(
            List<String> grades,
            List<String> regions
    ) {
    }

    public record TalentExplorerEmployee(
            String employeeId,
            String initials,
            String name,
            String grade,
            String role,
            List<String> skills,
            String location,
            String availability,
            LocalDate expectedReleaseDate,
            String domain,
            String department,
            int fitScore
    ) {
    }

    public record PersonProfileResponse(
            PersonProfileSummary summary,
            List<PersonProfileSkill> skills,
            List<String> domains,
            PersonProfileDetails profile,
            List<PersonAllocation> allocations,
            List<PersonProjectEvidence> projectEvidence,
            List<PersonAvailability> availabilityForecast
    ) {
    }

    public record PersonProfileSummary(
            String employeeId,
            String initials,
            String name,
            String grade,
            String role,
            String location,
            String region,
            String country,
            String city,
            String department,
            String discipline,
            String availability,
            String availabilityCategory,
            String currentProject,
            LocalDate currentProjectEnd,
            String ewaStatus,
            String workMode,
            BigDecimal currentAllocationFTE,
            BigDecimal availableFTECurrent
    ) {
    }

    public record PersonProfileSkill(
            String skillName,
            String skillCategory,
            Integer skillLevel,
            BigDecimal yearsExperience,
            LocalDate lastUsedDate,
            String evidenceSource,
            String confidence
    ) {
    }

    public record PersonProfileDetails(
            String profileSummary,
            List<String> keyStrengths,
            List<String> preferredWorkTypes,
            Map<String, String> domainExperienceSummary,
            List<String> certifications,
            List<String> recentHighlights,
            String mobilityNotes,
            List<String> languages
    ) {
    }

    public record PersonAllocation(
            String projectName,
            String clientName,
            String clientType,
            String domain,
            String roleOnProject,
            BigDecimal allocationFTE,
            LocalDate startDate,
            LocalDate plannedEndDate,
            String allocationStatus,
            String ewaStatus
    ) {
    }

    public record PersonProjectEvidence(
            String projectName,
            String clientName,
            String clientType,
            String domain,
            String role,
            LocalDate startDate,
            LocalDate endDate,
            List<String> keyTechnologiesOrMethods,
            List<String> responsibilities,
            List<String> outcomeEvidence,
            String region,
            Integer teamSize
    ) {
    }

    public record PersonAvailability(
            LocalDate weekStartDate,
            BigDecimal availableFTE,
            String availabilityType,
            String source,
            String confidence,
            String ewaStatus,
            String notes
    ) {
    }
}
