package com.utp.proyectoFinal.controller;

import com.utp.proyectoFinal.dto.JobDTO;
import com.utp.proyectoFinal.dto.JobRequest;
import com.utp.proyectoFinal.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Job posting management endpoints")
public class JobController {

    private final JobService jobService;

    @Operation(
        summary = "Create a new job posting",
        description = "Creates a new job posting with the provided details. Only accessible by recruiters and admins."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job created successfully",
            content = @Content(schema = @Schema(implementation = JobDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<JobDTO> createJob(@Valid @RequestBody JobRequest jobRequest) {
        return ResponseEntity.ok(jobService.createJob(jobRequest));
    }

    @Operation(
        summary = "Get all active jobs",
        description = "Retrieves all active job postings with pagination. Accessible by all users."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of active jobs retrieved successfully",
            content = @Content(schema = @Schema(implementation = Page.class)))
    })
    @GetMapping
    public ResponseEntity<Page<JobDTO>> getAllJobs(
            @Parameter(description = "Pagination parameters")
            Pageable pageable
    ) {
        return ResponseEntity.ok(jobService.getAllJobs(pageable));
    }

    @Operation(
        summary = "Get job by ID",
        description = "Retrieves a specific job posting by its ID. Accessible by all users."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job found",
            content = @Content(schema = @Schema(implementation = JobDTO.class))),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<JobDTO> getJob(
            @Parameter(description = "Job ID")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @Operation(
        summary = "Update job posting",
        description = "Updates an existing job posting. Only accessible by the recruiter who created the job and admins."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job updated successfully",
            content = @Content(schema = @Schema(implementation = JobDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<JobDTO> updateJob(
            @Parameter(description = "Job ID")
            @PathVariable Long id,
            @Valid @RequestBody JobRequest jobRequest
    ) {
        return ResponseEntity.ok(jobService.updateJob(id, jobRequest));
    }

    @Operation(
        summary = "Delete job posting",
        description = "Deletes (deactivates) an existing job posting. Only accessible by the recruiter who created the job and admins."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job deleted successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteJob(
            @Parameter(description = "Job ID")
            @PathVariable Long id
    ) {
        jobService.deleteJob(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Get jobs by recruiter",
        description = "Retrieves all job postings created by the specified recruiter. Only accessible by recruiters."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of jobs retrieved successfully",
            content = @Content(schema = @Schema(implementation = List.class)))
    })
    @GetMapping("/recruiter/{recruiterId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<List<JobDTO>> getJobsByRecruiter(
            @Parameter(description = "Recruiter ID")
            @PathVariable Long recruiterId
    ) {
        return ResponseEntity.ok(jobService.getJobsByRecruiter(recruiterId));
    }

    @Operation(
        summary = "Search jobs",
        description = "Searches for jobs based on various criteria. Accessible by all users."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully",
            content = @Content(schema = @Schema(implementation = Page.class)))
    })
    @GetMapping("/search")
    public ResponseEntity<Page<JobDTO>> searchJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String skillLevel,
            Pageable pageable
    ) {
        return ResponseEntity.ok(jobService.searchJobs(keyword, location, skillLevel, pageable));
    }

    @Operation(
        summary = "Generate interview questions for a job",
        description = "Generates AI-powered interview questions based on the job description. Only accessible by recruiters and admins."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Questions generated successfully",
            content = @Content(schema = @Schema(implementation = List.class))),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @GetMapping("/{id}/questions")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    public ResponseEntity<List<String>> generateQuestionsForJob(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") int count
    ) {
        return ResponseEntity.ok(jobService.generateQuestionsForJob(id, count));
    }
}
