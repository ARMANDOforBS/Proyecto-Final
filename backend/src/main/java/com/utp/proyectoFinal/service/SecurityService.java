package com.utp.proyectoFinal.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.utp.proyectoFinal.model.Applicant;
import com.utp.proyectoFinal.model.Role;
import com.utp.proyectoFinal.model.User;
import com.utp.proyectoFinal.repository.ApplicantRepository;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final ApplicantRepository applicantRepository;

    /**
     * Checks if the authenticated user is the owner of the user account
     * 
     * @param userId ID of the user to check
     * @param authentication Current authentication object
     * @return true if the authenticated user is the owner or an admin
     */
    public boolean isUserOwner(Long userId, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        User authenticatedUser = (User) authentication.getPrincipal();
        
        // Admin can access any user
        if (authenticatedUser.getRole() == Role.ADMIN) {
            return true;
        }
        
        // Check if the authenticated user is the owner
        return authenticatedUser.getId().equals(userId);
    }
    
    /**
     * Checks if the authenticated user is the owner of the applicant profile
     * 
     * @param applicantId ID of the applicant to check
     * @param authentication Current authentication object
     * @return true if the authenticated user is the owner or an admin
     */
    public boolean isApplicantOwner(Long applicantId, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        User authenticatedUser = (User) authentication.getPrincipal();
        
        // Admin can access any applicant
        if (authenticatedUser.getRole() == Role.ADMIN) {
            return true;
        }
        
        // Get the applicant
        return applicantRepository.findById(applicantId)
                .map(applicant -> applicant.getUser().getId().equals(authenticatedUser.getId()))
                .orElse(false);
    }
    
    /**
     * Checks if the authenticated user is the owner of the test result
     * 
     * @param testResultId ID of the test result to check
     * @param authentication Current authentication object
     * @return true if the authenticated user is the owner or an admin/recruiter
     */
    public boolean isTestResultOwner(Long testResultId, Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        
        User authenticatedUser = (User) authentication.getPrincipal();
        
        // Admin and Recruiter can access any test result
        if (authenticatedUser.getRole() == Role.ADMIN || authenticatedUser.getRole() == Role.RECRUITER) {
            return true;
        }
        
        // For applicants, check if they own the test result
        if (authenticatedUser.getRole() == Role.APPLICANT) {
            // Find the applicant associated with the authenticated user
            Applicant applicant = applicantRepository.findByUser(authenticatedUser)
                    .orElse(null);
            
            if (applicant == null) {
                return false;
            }
            
            // Check if the test result belongs to this applicant
            return applicant.getTestResults().stream()
                    .anyMatch(result -> result.getId().equals(testResultId));
        }
        
        return false;
    }

    /**
     * Checks if the authenticated user can start a test for the given applicant
     *
     * @param request Map containing applicantId and testId
     * @param authentication The current authentication
     * @return true if the user is authorized
     */
    public boolean canStartTest(Map<String, Long> request, Authentication authentication) {
        Long applicantId = request.get("applicantId");
        return applicantId != null && isApplicantOwner(applicantId, authentication);
    }
}
