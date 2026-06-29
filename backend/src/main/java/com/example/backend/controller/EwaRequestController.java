package com.example.backend.controller;

import com.example.backend.dto.EwaRequestSubmitRequest;
import com.example.backend.entity.EwaRequest;
import com.example.backend.service.EwaRequestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ewa-requests")
public class EwaRequestController {

    private final EwaRequestService ewaRequestService;

    public EwaRequestController(EwaRequestService ewaRequestService) {
        this.ewaRequestService = ewaRequestService;
    }

    @PostMapping("/submit")
    public EwaRequest submitToEwa(@RequestBody EwaRequestSubmitRequest request) {
        return ewaRequestService.submitToEwa(request);
    }
}
