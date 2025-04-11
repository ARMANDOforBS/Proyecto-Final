package com.utp.proyectoFinal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.utp.proyectoFinal.model.Applicant;
import com.utp.proyectoFinal.model.ApplicantSkill;

@Repository
public interface ApplicantSkillRepository extends JpaRepository<ApplicantSkill, Long> {
    List<ApplicantSkill> findByApplicant(Applicant applicant);
    
    List<ApplicantSkill> findBySkillNameContainingIgnoreCase(String skillName);
    
    @Query("SELECT s FROM ApplicantSkill s WHERE s.isVerified = true")
    List<ApplicantSkill> findVerifiedSkills();
    
    @Query("SELECT s FROM ApplicantSkill s WHERE s.aiValidated = true")
    List<ApplicantSkill> findAiValidatedSkills();
}
