package com.utp.proyectoFinal.repository;

import com.utp.proyectoFinal.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    Page<Job> findByActiveTrue(Pageable pageable);
    List<Job> findByActiveTrue();
    List<Job> findByRecruiterIdAndActiveTrue(Long recruiterId);
    
    // Search methods
    Page<Job> findByTitleContainingOrDescriptionContainingAndActiveTrue(
            String title, String description, Pageable pageable);
            
    Page<Job> findByTitleContainingOrDescriptionContainingAndSkillLevelAndActiveTrue(
            String title, String description, String skillLevel, Pageable pageable);
            
    Page<Job> findByTitleContainingOrDescriptionContainingAndLocationContainingAndActiveTrue(
            String title, String description, String location, Pageable pageable);
            
    Page<Job> findByTitleContainingOrDescriptionContainingAndLocationContainingAndSkillLevelAndActiveTrue(
            String title, String description, String location, String skillLevel, Pageable pageable);
            
    Page<Job> findByLocationContainingAndActiveTrue(String location, Pageable pageable);
    
    Page<Job> findByLocationContainingAndSkillLevelAndActiveTrue(
            String location, String skillLevel, Pageable pageable);
            
    Page<Job> findBySkillLevelAndActiveTrue(String skillLevel, Pageable pageable);
}
