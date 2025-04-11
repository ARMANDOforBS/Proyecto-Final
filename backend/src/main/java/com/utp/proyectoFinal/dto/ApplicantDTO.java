package com.utp.proyectoFinal.dto;

import java.time.LocalDate;
import java.util.List;

import com.utp.proyectoFinal.model.ApplicantStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicantDTO {
    private Long id;
    private UserDTO user;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String address;
    private String education;
    private String skills;
    private String experience;
    private String cvPath;
    private String profilePicturePath;
    private ApplicantStatus status;
    private Double aiScore;
    private List<TestResultDTO> testResults;
    private List<ApplicantSkillDTO> applicantSkills;
    
    // Campos adicionales para aplicaciones a trabajos
    private Long jobId;
    private String jobTitle;
}
