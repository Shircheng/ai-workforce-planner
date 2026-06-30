package com.example.backend.service;

import com.example.backend.entity.Bench;
import com.example.backend.entity.Allocation;
import com.example.backend.entity.Availability;
import com.example.backend.entity.Employee;
import com.example.backend.entity.EmployeeSkill;
import com.example.backend.entity.Opportunity;
import com.example.backend.entity.OpportunityRole;
import com.example.backend.entity.Profile;
import com.example.backend.entity.ProjectHistory;
import com.example.backend.repository.AllocationRepository;
import com.example.backend.repository.AvailabilityRepository;
import com.example.backend.repository.BenchRepository;
import com.example.backend.repository.EmployeeRepository;
import com.example.backend.repository.EmployeeSkillRepository;
import com.example.backend.repository.OpportunityRepository;
import com.example.backend.repository.OpportunityRoleRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final BenchRepository benchRepository;
    private final ProfileRepository profileRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final AllocationRepository allocationRepository;
    private final AvailabilityRepository availabilityRepository;
    private final OpportunityRepository opportunityRepository;
    private final OpportunityRoleRepository opportunityRoleRepository;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            EmployeeSkillRepository employeeSkillRepository,
            BenchRepository benchRepository,
            ProfileRepository profileRepository,
            ProjectHistoryRepository projectHistoryRepository,
            AllocationRepository allocationRepository,
            AvailabilityRepository availabilityRepository,
            OpportunityRepository opportunityRepository,
            OpportunityRoleRepository opportunityRoleRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.benchRepository = benchRepository;
        this.profileRepository = profileRepository;
        this.projectHistoryRepository = projectHistoryRepository;
        this.allocationRepository = allocationRepository;
        this.availabilityRepository = availabilityRepository;
        this.opportunityRepository = opportunityRepository;
        this.opportunityRoleRepository = opportunityRoleRepository;
    }

    public EmployeePageResponse listEmployees(
            int page,
            int size,
            String skillSearch,
            String availability,
            String region,
            String grade,
            String status,
            String role,
            String domain
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
                        skillSearch,
                        grade,
                        region
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

    public WorkforceDashboardResponse getDashboard() {
        List<Employee> employees = employeeRepository.findAll();
        List<Opportunity> opportunities = opportunityRepository.findAll();
        List<OpportunityRole> opportunityRoles = opportunityRoleRepository.findAll();

        if (employees.isEmpty() && opportunities.isEmpty() && opportunityRoles.isEmpty()) {
            return emptyDashboard();
        }

        List<String> employeeIds = employees.stream()
                .map(Employee::getEmployeeId)
                .filter(Objects::nonNull)
                .toList();
        List<EmployeeSkill> employeeSkills = employeeIds.isEmpty()
                ? List.of()
                : employeeSkillRepository.findByEmployeeIdIn(employeeIds);
        Map<String, Bench> benchByEmployee = employeeIds.isEmpty()
                ? Map.of()
                : benchRepository.findByEmployeeIdIn(employeeIds)
                .stream()
                .collect(Collectors.toMap(
                        Bench::getEmployeeId,
                        bench -> bench,
                        (first, ignored) -> first
                ));
        long availableCount = employees.stream()
                .filter(employee -> isBenchOrAvailable(employee, benchByEmployee.get(employee.getEmployeeId())))
                .count();
        long rollingOffCount = employees.stream()
                .filter(this::isRollingOff)
                .count();
        long openRoleCount = opportunityRoles.isEmpty() ? opportunities.size() : opportunityRoles.size();

        return new WorkforceDashboardResponse(
                dashboardMetrics(employees.size(), availableCount, rollingOffCount, openRoleCount),
                availabilityOutlook(employees, benchByEmployee),
                topCounts(employees.stream()
                        .map(employee -> firstNonBlank(
                                employee.getRoleArchetype(),
                                employee.getCurrentRole(),
                                employee.getDiscipline(),
                                "Unassigned Role"
                        ))
                        .toList(), 5),
                topSkills(employeeSkills, benchByEmployee),
                topCounts(employees.stream()
                        .map(employee -> firstNonBlank(
                                employee.getRegion(),
                                employee.getCountry(),
                                employee.getCity(),
                                "Unassigned Region"
                        ))
                        .toList(), 5),
                demandByDomain(opportunities, opportunityRoles),
                dashboardAlerts(availableCount, rollingOffCount, openRoleCount)
        );
    }

    public Employee getEmployee(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Employee not found: " + employeeId
                ));
    }

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

    public PersonProfileResponse getPersonProfile(String employeeId) {
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

    private WorkforceDashboardResponse emptyDashboard() {
        return new WorkforceDashboardResponse(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private List<DashboardMetric> dashboardMetrics(
            long totalEmployees,
            long availableCount,
            long rollingOffCount,
            long openRoleCount
    ) {
        return List.of(
                new DashboardMetric("Total Workforce", totalEmployees, "Imported employees"),
                new DashboardMetric("Available Now", availableCount, "Bench or current capacity"),
                new DashboardMetric("Rolling Off", rollingOffCount, "Release dates tracked"),
                new DashboardMetric("Open Roles", openRoleCount, "Demand records")
        );
    }

    private List<DashboardBar> availabilityOutlook(
            List<Employee> employees,
            Map<String, Bench> benchByEmployee
    ) {
        long availableNow = 0;
        long available30 = 0;
        long available60 = 0;
        long available90 = 0;
        long availableLater = 0;

        for (Employee employee : employees) {
            long daysUntilAvailable = daysUntilAvailable(employee, benchByEmployee.get(employee.getEmployeeId()));
            if (daysUntilAvailable <= 0) {
                availableNow++;
            } else if (daysUntilAvailable <= 30) {
                available30++;
            } else if (daysUntilAvailable <= 60) {
                available60++;
            } else if (daysUntilAvailable <= 90) {
                available90++;
            } else {
                availableLater++;
            }
        }

        return List.of(
                new DashboardBar("Now", availableNow),
                new DashboardBar("30 days", available30),
                new DashboardBar("60 days", available60),
                new DashboardBar("90 days", available90),
                new DashboardBar("90+ days", availableLater)
        );
    }

    private List<DashboardBar> topSkills(
            List<EmployeeSkill> employeeSkills,
            Map<String, Bench> benchByEmployee
    ) {
        List<String> skillNames = new ArrayList<>(employeeSkills.stream()
                .map(EmployeeSkill::getSkillName)
                .filter(Objects::nonNull)
                .toList());

        if (skillNames.isEmpty()) {
            benchByEmployee.values().stream()
                    .map(Bench::getTopSkills)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .forEach(skillNames::add);
        }

        return topCounts(skillNames, 8);
    }

    private List<DashboardBar> demandByDomain(
            List<Opportunity> opportunities,
            List<OpportunityRole> opportunityRoles
    ) {
        List<String> domains = new ArrayList<>();
        opportunities.stream()
                .map(Opportunity::getDomain)
                .filter(Objects::nonNull)
                .forEach(domains::add);
        opportunityRoles.stream()
                .map(role -> firstNonBlank(
                        role.getDomainExperienceRequired(),
                        role.getDisciplineOrDepartment()
                ))
                .filter(value -> !value.isBlank())
                .forEach(domains::add);

        return topCounts(domains, 5);
    }

    private List<DashboardAlert> dashboardAlerts(
            long availableCount,
            long rollingOffCount,
            long openRoleCount
    ) {
        List<DashboardAlert> alerts = new ArrayList<>();

        if (availableCount > 0) {
            alerts.add(new DashboardAlert(
                    "Available capacity",
                    availableCount + " people are currently available or listed on bench.",
                    "success"
            ));
        }

        if (openRoleCount > availableCount && openRoleCount > 0) {
            alerts.add(new DashboardAlert(
                    "Demand exceeds available capacity",
                    openRoleCount + " open roles are competing for " + availableCount + " available people.",
                    "warning"
            ));
        } else if (openRoleCount > 0) {
            alerts.add(new DashboardAlert(
                    "Demand coverage available",
                    "Current available capacity can cover the open role count.",
                    "info"
            ));
        }

        if (rollingOffCount > 0) {
            alerts.add(new DashboardAlert(
                    "Upcoming roll-offs",
                    rollingOffCount + " people have release dates that can support near-term planning.",
                    "info"
            ));
        }

        if (alerts.isEmpty()) {
            alerts.add(new DashboardAlert(
                    "Dataset imported",
                    "Dashboard metrics are ready for workforce planning.",
                    "info"
            ));
        }

        return alerts.stream().limit(3).toList();
    }

    private List<DashboardBar> topCounts(List<String> values, int limit) {
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted((first, second) -> {
                    int countComparison = Long.compare(second.getValue(), first.getValue());
                    if (countComparison != 0) {
                        return countComparison;
                    }
                    return first.getKey().compareToIgnoreCase(second.getKey());
                })
                .limit(limit)
                .map(entry -> new DashboardBar(entry.getKey(), entry.getValue()))
                .toList();
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
                .anyMatch(term -> candidateSkills.stream().anyMatch(skillName -> skillMatches(term, skillName)));
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
            String skillSearch,
            String targetGrade,
            String selectedRegion
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
                skillSearch,
                targetGrade,
                selectedRegion
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
            String skillSearch,
            String targetGrade,
            String selectedRegion
    ) {
        double skillScore = skillScore(employeeSkills, skillSearch);
        double availabilityScore = availabilityDaysScore(daysUntilAvailable(employee, bench));
        double gradeScore = gradeScore(targetGrade, employee.getGrade());
        double statusScore = statusScore(employee, bench);
        double regionScore = regionScore(employee, selectedRegion);

        double weightedScore = skillScore * 0.50
                + availabilityScore * 0.25
                + gradeScore * 0.10
                + statusScore * 0.10
                + regionScore * 0.05;

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

        long matchedSkills = searchTerms.stream()
                .filter(term -> candidateSkills.stream().anyMatch(skillName -> skillMatches(term, skillName)))
                .count();

        return (double) matchedSkills / searchTerms.size() * 100;
    }

    private boolean skillMatches(String searchTerm, String candidateSkill) {
        return candidateSkill.equals(searchTerm)
                || candidateSkill.contains(searchTerm)
                || searchTerm.contains(candidateSkill);
    }

    private double availabilityDaysScore(long days) {
        if (days <= 0) {
            return 100;
        }
        if (days <= 15) {
            return 90;
        }
        if (days <= 30) {
            return 80;
        }
        if (days <= 60) {
            return 65;
        }
        if (days <= 90) {
            return 45;
        }
        if (days <= 120) {
            return 25;
        }
        return 10;
    }

    private long daysUntilAvailable(Employee employee, Bench bench) {
        LocalDate releaseDate = firstDate(employee.getExpectedReleaseDate(), bench == null ? null : bench.getAvailableFrom());
        if (releaseDate != null) {
            return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), releaseDate));
        }

        BigDecimal availableFte = employee.getAvailableFTECurrent();
        if (
                bench != null
                        || containsAny(employee.getAvailabilityCategory(), Set.of("bench", "available"))
                        || (availableFte != null && availableFte.compareTo(BigDecimal.ZERO) > 0)
        ) {
            return 0;
        }

        return 121;
    }

    private double statusScore(Employee employee, Bench bench) {
        if (containsAny(employee.getAvailabilityCategory(), Set.of("blocked", "inactive", "unavailable"))) {
            return 0;
        }
        if (isBenchOrAvailable(employee, bench)) {
            return 100;
        }
        if (isRollingOff(employee)) {
            return 85;
        }

        BigDecimal currentAllocation = employee.getCurrentAllocationFTE();
        if (currentAllocation != null && currentAllocation.compareTo(BigDecimal.ZERO) > 0) {
            return 40;
        }

        return 0;
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

    private double regionScore(Employee employee, String selectedRegion) {
        String normalizedRegion = normalize(selectedRegion);
        if (normalizedRegion.isBlank() || "all".equals(normalizedRegion) || "any".equals(normalizedRegion)) {
            return 100;
        }

        String candidateRegion = normalize(employee.getRegion());
        return !candidateRegion.isBlank()
                && (candidateRegion.equals(normalizedRegion)
                || candidateRegion.contains(normalizedRegion)
                || normalizedRegion.contains(candidateRegion))
                ? 100
                : 0;
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

        int levelDifference = candidateIndex - targetIndex;
        if (levelDifference == 0) {
            return 100;
        }
        if (levelDifference == 1) {
            return 90;
        }
        if (levelDifference == -1) {
            return 75;
        }
        if (levelDifference == 2) {
            return 75;
        }
        if (levelDifference == -2) {
            return 50;
        }
        if (levelDifference == 3) {
            return 50;
        }
        return 25;
    }

    private int gradeIndex(String normalizedGrade) {
        return List.of(
                "associateconsultant",
                "consultant",
                "seniorconsultant",
                "leadconsultant",
                "manager",
                "seniormanager",
                "principalconsultant"
        ).indexOf(normalizedGrade);
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

    public record WorkforceDashboardResponse(
            List<DashboardMetric> metrics,
            List<DashboardBar> availabilityOutlook,
            List<DashboardBar> supplyByRole,
            List<DashboardBar> topSkills,
            List<DashboardBar> regions,
            List<DashboardBar> demandByDomain,
            List<DashboardAlert> alerts
    ) {
    }

    public record DashboardMetric(
            String label,
            long value,
            String note
    ) {
    }

    public record DashboardBar(
            String label,
            long value
    ) {
    }

    public record DashboardAlert(
            String title,
            String message,
            String tone
    ) {
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
