package com.utp.proyectoFinal.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.utp.proyectoFinal.dto.QuestionDTO;
import com.utp.proyectoFinal.dto.TestDTO;
import com.utp.proyectoFinal.model.TestType;
import com.utp.proyectoFinal.service.TestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tests")
@RequiredArgsConstructor
@Tag(name = "Test Management", description = "APIs for managing tests and questions")
public class TestController {

    private final TestService testService;

    @Operation(
        summary = "Get all tests",
        description = "Retrieves a list of all tests"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of tests retrieved successfully",
            content = @Content(schema = @Schema(implementation = List.class))),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping
    public ResponseEntity<List<TestDTO>> getAllTests() {
        return ResponseEntity.ok(testService.getAllTests());
    }

    @Operation(
        summary = "Get test by ID",
        description = "Retrieves a test by its ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test found",
            content = @Content(schema = @Schema(implementation = TestDTO.class))),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Test not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<TestDTO> getTestById(
            @Parameter(description = "Test ID")
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(testService.getTestById(id));
    }

    @Operation(
        summary = "Get tests by type",
        description = "Retrieves tests filtered by their type"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of tests retrieved successfully",
            content = @Content(schema = @Schema(implementation = List.class))),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/type/{type}")
    public ResponseEntity<List<TestDTO>> getTestsByType(
            @Parameter(description = "Test type")
            @PathVariable TestType type
    ) {
        return ResponseEntity.ok(testService.getTestsByType(type));
    }

    @Operation(
        summary = "Create test",
        description = "Creates a new test"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Test created successfully",
            content = @Content(schema = @Schema(implementation = TestDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<TestDTO> createTest(
            @Valid @RequestBody TestDTO testDto
    ) {
        return new ResponseEntity<>(testService.createTest(testDto), HttpStatus.CREATED);
    }

    @Operation(
        summary = "Update test",
        description = "Updates an existing test"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Test updated successfully",
            content = @Content(schema = @Schema(implementation = TestDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Test not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<TestDTO> updateTest(
            @Parameter(description = "Test ID")
            @PathVariable Long id,
            @Valid @RequestBody TestDTO testDto
    ) {
        return ResponseEntity.ok(testService.updateTest(id, testDto));
    }

    @Operation(
        summary = "Delete test",
        description = "Deletes a test by its ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Test deleted successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Test not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTest(
            @Parameter(description = "Test ID")
            @PathVariable Long id
    ) {
        testService.deleteTest(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Add question to test",
        description = "Adds a new question to an existing test"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Question added successfully",
            content = @Content(schema = @Schema(implementation = TestDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Test not found")
    })
    @PostMapping("/{testId}/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<TestDTO> addQuestionToTest(
            @Parameter(description = "Test ID")
            @PathVariable Long testId,
            @Valid @RequestBody QuestionDTO questionDto
    ) {
        return ResponseEntity.ok(testService.addQuestionToTest(testId, questionDto));
    }

    @Operation(
        summary = "Remove question from test",
        description = "Removes a question from a test"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Question removed successfully",
            content = @Content(schema = @Schema(implementation = TestDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Test not found")
    })
    @DeleteMapping("/{testId}/questions/{questionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<TestDTO> removeQuestionFromTest(
            @Parameter(description = "Test ID")
            @PathVariable Long testId,
            @Parameter(description = "Question ID")
            @PathVariable Long questionId
    ) {
        return ResponseEntity.ok(testService.removeQuestionFromTest(testId, questionId));
    }

    @Operation(
        summary = "Generate AI questions",
        description = "Generates questions for a test using AI"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Questions generated successfully",
            content = @Content(schema = @Schema(implementation = TestDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Test not found")
    })
    @PostMapping("/{testId}/generate-questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER')")
    public ResponseEntity<TestDTO> generateAIQuestions(
            @Parameter(description = "Test ID")
            @PathVariable Long testId,
            @Parameter(description = "Topic")
            @RequestParam String topic,
            @Parameter(description = "Number of questions to generate")
            @RequestParam(defaultValue = "5") int count
    ) {
        return ResponseEntity.ok(testService.generateAIQuestions(testId, topic, count));
    }

    @Operation(
        summary = "Get questions for a test",
        description = "Retrieves the list of questions for a specific test"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "List of questions retrieved successfully",
            content = @Content(schema = @Schema(implementation = List.class))),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Test not found")
    })
    @GetMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECRUITER', 'APPLICANT')")
    public ResponseEntity<List<QuestionDTO>> getQuestionsForTest(
            @Parameter(description = "Test ID")
            @PathVariable Long id
    ) {
        TestDTO testDto = testService.getTestById(id);
        return ResponseEntity.ok(testDto.getQuestions());
    }
}
