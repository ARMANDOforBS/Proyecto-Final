package com.utp.proyectoFinal.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.utp.proyectoFinal.model.Applicant;
import com.utp.proyectoFinal.model.ApplicantStatus;
import com.utp.proyectoFinal.model.User;

@Repository
public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    Optional<Applicant> findByUser(User user);
    
    boolean existsByUserId(@Param("userId") Long userId);
    
    List<Applicant> findByStatus(ApplicantStatus status);
    
    @Query("SELECT a FROM Applicant a WHERE a.aiScore >= :minScore ORDER BY a.aiScore DESC")
    List<Applicant> findTopApplicantsByAiScore(@Param("minScore") Double minScore);
    
    @Query("SELECT a FROM Applicant a JOIN a.applicantSkills s WHERE s.skillName LIKE %:skill% AND s.isVerified = true")
    List<Applicant> findByVerifiedSkill(String skill);
    
    List<Applicant> findByUserId(Long userId);
    
    List<Applicant> findByJobId(Long jobId);
    
    boolean existsByUserIdAndJobId(Long userId, Long jobId);
}
