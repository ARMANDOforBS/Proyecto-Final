package com.utp.proyectoFinal.dto;

import com.utp.proyectoFinal.model.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRequest {
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;
    
    @NotBlank(message = "Skill level is required")
    private String skillLevel;
    
    @NotBlank(message = "Location is required")
    private String location;
    
    private String salaryRange;
    
    private String requiredExperience;
    
    @NotNull(message = "Employment type is required")
    private EmploymentType employmentType;
    
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();
    
    private LocalDateTime closingDate;
}
