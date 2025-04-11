package com.utp.proyectoFinal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.utp.proyectoFinal.dto.ApplicantDTO;
import com.utp.proyectoFinal.dto.ApplicantRequest;
import com.utp.proyectoFinal.service.ApplicantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/applicants")
@RequiredArgsConstructor
@Tag(name = "Applicant Management", description = "APIs for managing applicants")
public class ApplicantController {

    private final ApplicantService applicantService;

    @PostMapping(value = "/jobs/{jobId}/apply", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('APPLICANT') or hasRole('ADMIN')")
    @Operation(summary = "Apply to job", description = "Submit a job application with CV")
    public ResponseEntity<ApplicantDTO> applyToJob(
            @PathVariable Long jobId,
            @Valid @RequestPart("request") ApplicantRequest request,
            @RequestPart("cv") MultipartFile cv) {
        return new ResponseEntity<>(applicantService.applyToJob(jobId, request, cv), HttpStatus.CREATED);
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "Get applicants by job", description = "Get all applicants for a specific job posting")
    public ResponseEntity<List<ApplicantDTO>> getApplicantsByJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(applicantService.getApplicantsByJob(jobId));
    }

    @GetMapping("/my-applications")
    @PreAuthorize("hasRole('APPLICANT') or hasRole('ADMIN')")
    @Operation(summary = "Get my applications", description = "Get all applications for the current user")
    public ResponseEntity<List<ApplicantDTO>> getMyApplications() {
        return ResponseEntity.ok(applicantService.getMyApplications());
    }

    @PutMapping("/jobs/{jobId}/applicants/{applicantId}/status")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "Update application status", description = "Update the status of a job application")
    public ResponseEntity<ApplicantDTO> updateApplicationStatus(
            @PathVariable Long jobId,
            @PathVariable Long applicantId,
            @RequestParam String status) {
        return ResponseEntity.ok(applicantService.updateApplicationStatus(jobId, applicantId, status));
    }
    
    @PostMapping("/{applicantId}/cv")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "Analyze CV", description = "Analyze the CV of an applicant using AI")
    public ResponseEntity<Map<String, Object>> analyzeCV(@PathVariable Long applicantId) {
        return ResponseEntity.ok(applicantService.analyzeCV(applicantId));
    }
    
    @GetMapping("")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "Get all applicants", description = "Get all applicants across all jobs")
    public ResponseEntity<List<ApplicantDTO>> getAllApplicants() {
        return ResponseEntity.ok(applicantService.getAllApplicants());
    }
}
