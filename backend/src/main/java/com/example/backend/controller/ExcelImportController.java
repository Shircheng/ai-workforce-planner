package com.example.backend.controller;

import com.example.backend.dto.ExcelImportResult;
import com.example.backend.service.EmployeeExcelImportService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class ExcelImportController {

    private final EmployeeExcelImportService employeeExcelImportService;

    public ExcelImportController(EmployeeExcelImportService employeeExcelImportService) {
        this.employeeExcelImportService = employeeExcelImportService;
    }

    @PostMapping("/workforce-dataset")
    public ExcelImportResult importWorkforceDataset() throws IOException {
        return employeeExcelImportService.importDefaultDataset();
    }

    @PostMapping(value = "/workforce-dataset/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExcelImportResult uploadWorkforceDataset(@RequestPart("file") MultipartFile file) throws IOException {
        return employeeExcelImportService.importWorkbook(file.getInputStream());
    }
}
