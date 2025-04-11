package com.utp.proyectoFinal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.utp.proyectoFinal.model.Applicant;
import com.utp.proyectoFinal.model.Test;
import com.utp.proyectoFinal.model.TestResult;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findByApplicant(Applicant applicant);
    
    List<TestResult> findByTest(Test test);
    
    @Query("SELECT tr FROM TestResult tr WHERE tr.applicant = :applicant AND tr.test = :test")
    List<TestResult> findByApplicantAndTest(Applicant applicant, Test test);
    
    @Query("SELECT tr FROM TestResult tr WHERE tr.score >= :minScore ORDER BY tr.score DESC")
    List<TestResult> findTopPerformers(Double minScore);
    
    boolean existsByApplicantAndTest(Applicant applicant, Test test);
    
    // Método personalizado para contar resultados completados
    @Query("SELECT COUNT(tr) FROM TestResult tr WHERE tr.applicant = :applicant AND tr.completedAt IS NOT NULL")
    long countCompletedByApplicant(Applicant applicant);
    
    long countByApplicantAndScoreIsNotNull(Applicant applicant);
    
    /**
     * Finds test results for a specific applicant and test where completedAt is null (test in progress)
     * @param applicant the applicant
     * @param test the test
     * @return the list of test results
     */
    List<TestResult> findByApplicantAndTestAndCompletedAtIsNull(Applicant applicant, Test test);
}
