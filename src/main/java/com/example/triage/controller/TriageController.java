package com.example.triage.controller;

import com.example.triage.model.TriageRequest;
import com.example.triage.model.TriageResponse;
import com.example.triage.service.TriageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class TriageController {
    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping("/triage")
    public ResponseEntity<TriageResponse> triage(@Valid @RequestBody TriageRequest request) {
        return ResponseEntity.ok(triageService.triage(request));
    }
}
