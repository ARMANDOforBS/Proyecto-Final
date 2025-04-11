package com.utp.proyectoFinal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.utp.proyectoFinal.dto.AnswerDTO;
import com.utp.proyectoFinal.dto.TestResultDTO;
import com.utp.proyectoFinal.service.TestResultService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/test-results")
@RequiredArgsConstructor
@Tag(name = "Test Result Management", description = "APIs for managing test results")
public class TestResultController {

    private final TestResultService testResultService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    @Operation(summary = "Get all test results", description = "Retrieves a list of all test results")
    public ResponseEntity<List<TestResultDTO>> getAllTestResults() {
        return ResponseEntity.ok(testResultService.getAllTestResults());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER') or @securityService.isTestResultOwner(#id, authentication)")
    @Operation(summary = "Get test result by ID", description = "Retrieves a test result by its ID")
    public ResponseEntity<TestResultDTO> getTestResultById(@PathVariable Long id) {
        return ResponseEntity.ok(testResultService.getTestResultById(id));
    }

    @GetMapping("/applicant/{applicantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER') or @securityService.isApplicantOwner(#applicantId, authentication)")
    @Operation(summary = "Get test results by applicant", description = "Retrieves test results for a specific applicant")
    public ResponseEntity<List<TestResultDTO>> getTestResultsByApplicantId(@PathVariable Long applicantId) {
        return ResponseEntity.ok(testResultService.getTestResultsByApplicantId(applicantId));
    }

    @GetMapping("/test/{testId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    @Operation(summary = "Get test results by test", description = "Retrieves test results for a specific test")
    public ResponseEntity<List<TestResultDTO>> getTestResultsByTestId(@PathVariable Long testId) {
        return ResponseEntity.ok(testResultService.getTestResultsByTestId(testId));
    }

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'APPLICANT') and @securityService.canStartTest(#request, authentication)")
    @Operation(summary = "Start test", description = "Starts a test for an applicant")
    public ResponseEntity<TestResultDTO> startTest(
            @RequestBody Map<String, Long> request) {
        Long applicantId = request.get("applicantId");
        Long testId = request.get("testId");
        return new ResponseEntity<>(testResultService.startTest(applicantId, testId), HttpStatus.CREATED);
    }

    @PutMapping("/{testResultId}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'APPLICANT') and @securityService.isTestResultOwner(#testResultId, authentication)")
    @Operation(summary = "Submit test answers", description = "Submits answers for a test")
    public ResponseEntity<TestResultDTO> submitTestAnswers(
            @PathVariable Long testResultId,
            @RequestBody Map<Long, AnswerDTO> answers) {
        return ResponseEntity.ok(testResultService.submitTestAnswers(testResultId, answers));
    }

    @PutMapping("/{testResultId}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    @Operation(summary = "Review test result", description = "Reviews and updates a test result")
    public ResponseEntity<TestResultDTO> reviewTestResult(
            @PathVariable Long testResultId,
            @RequestBody Map<Long, Boolean> reviewResults) {
        return ResponseEntity.ok(testResultService.reviewTestResult(testResultId, reviewResults));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete test result", description = "Deletes a test result by its ID")
    public ResponseEntity<Void> deleteTestResult(@PathVariable Long id) {
        testResultService.deleteTestResult(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my test results", description = "Retrieves test results for the current authenticated user")
    public ResponseEntity<List<TestResultDTO>> getMyTestResults() {
        return ResponseEntity.ok(testResultService.getTestResultsForCurrentUser());
    }
}
