package com.example.backend.controller;

import com.example.backend.entity.Employee;
import com.example.backend.service.EmployeeService;
import com.example.backend.service.EmployeeService.EmployeeFilterOptions;
import com.example.backend.service.EmployeeService.EmployeePageResponse;
import com.example.backend.service.EmployeeService.PersonProfileResponse;
import com.example.backend.service.EmployeeService.WorkforceDashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public EmployeePageResponse listEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String skillSearch,
            @RequestParam(defaultValue = "all") String availability,
            @RequestParam(defaultValue = "all") String region,
            @RequestParam(defaultValue = "all") String grade,
            @RequestParam(defaultValue = "all") String role,
            @RequestParam(defaultValue = "all") String domain
    ) {
        return employeeService.listEmployees(
                page,
                size,
                skillSearch,
                availability,
                region,
                grade,
                role,
                domain
        );
    }

    @GetMapping("/dashboard")
    public WorkforceDashboardResponse getDashboard() {
        return employeeService.getDashboard();
    }

    @GetMapping("/{employeeId}")
    public Employee getEmployee(@PathVariable String employeeId) {
        return employeeService.getEmployee(employeeId);
    }

    @GetMapping("/filter-options")
    public EmployeeFilterOptions getFilterOptions() {
        return employeeService.getFilterOptions();
    }

    @GetMapping("/{employeeId}/profile")
    public PersonProfileResponse getPersonProfile(@PathVariable String employeeId) {
        return employeeService.getPersonProfile(employeeId);
    }
}
