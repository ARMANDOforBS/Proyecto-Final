package com.utp.proyectoFinal.model.request;

import com.utp.proyectoFinal.model.EmploymentType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class JobRequest {
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 100, message = "Title must be between 5 and 100 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 50, max = 2000, message = "Description must be between 50 and 2000 characters")
    private String description;

    @NotBlank(message = "Skill level is required")
    private String skillLevel;

    @NotBlank(message = "Location is required")
    private String location;

    private String salaryRange;

    @NotBlank(message = "Required experience is required")
    private String requiredExperience;

    @NotNull(message = "Employment type is required")
    private EmploymentType employmentType;

    @NotNull(message = "Required skills cannot be null")
    @Size(min = 1, message = "At least one required skill must be specified")
    private List<String> requiredSkills;

    @Future(message = "Closing date must be in the future")
    private LocalDateTime closingDate;
}
