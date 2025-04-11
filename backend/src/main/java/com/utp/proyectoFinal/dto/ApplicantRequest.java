package com.utp.proyectoFinal.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicantRequest {
    private String firstName;
    private String lastName;
    
    @Email(message = "Please provide a valid email address")
    private String email;
    
    @Pattern(regexp = "^\\+?[0-9]{6,15}$", message = "Phone number must be between 6 and 15 digits, optionally starting with +")
    private String phoneNumber;
    
    private String address;
    
    private LocalDate dateOfBirth;
    
    @NotBlank(message = "Education is required")
    private String education;

    @NotBlank(message = "Skills are required")
    private String skills;

    @NotBlank(message = "Experience is required")
    private String experience;
    
    private String portfolioUrl;
    
    private String coverLetter;
}
