package com.utp.proyectoFinal.dto;

import com.utp.proyectoFinal.model.EmploymentType;
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
public class JobDTO {
    private Long id;
    private String title;
    private String description;
    private String skillLevel;
    private String location;
    private String salaryRange;
    private String requiredExperience;
    private EmploymentType employmentType;
    @Builder.Default
    private List<String> requiredSkills = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closingDate;
    private boolean active;
    private Long recruiterId;
    private String recruiterName;
}
