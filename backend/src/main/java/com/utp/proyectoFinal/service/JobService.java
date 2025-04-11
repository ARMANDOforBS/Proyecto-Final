package com.utp.proyectoFinal.service;

import com.utp.proyectoFinal.dto.JobDTO;
import com.utp.proyectoFinal.dto.JobRequest;
import com.utp.proyectoFinal.mapper.JobMapper;
import com.utp.proyectoFinal.model.Job;
import com.utp.proyectoFinal.model.User;
import com.utp.proyectoFinal.repository.JobRepository;
import com.utp.proyectoFinal.repository.UserRepository;
import com.utp.proyectoFinal.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final JobMapper jobMapper;

    @Transactional
    public JobDTO createJob(JobRequest request) {
        String userEmail = SecurityUtils.getCurrentUserId(); // Esto devuelve el email del usuario
        User recruiter = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        Job job = jobMapper.toEntity(request, recruiter);
        Job savedJob = jobRepository.save(job);
        
        return jobMapper.toDTO(savedJob);
    }

    public Page<JobDTO> getAllJobs(Pageable pageable) {
        Page<Job> jobs = jobRepository.findByActiveTrue(pageable);
        return jobMapper.toDTOPage(jobs);
    }
    
    public List<JobDTO> getAllActiveJobs() {
        List<Job> jobs = jobRepository.findByActiveTrue();
        return jobMapper.toDTOList(jobs);
    }

    public JobDTO getJobById(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job not found with id: " + id));
        return jobMapper.toDTO(job);
    }

    @Transactional
    public JobDTO updateJob(Long id, JobRequest request) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job not found with id: " + id));
        
        Long userId = Long.parseLong(SecurityUtils.getCurrentUserId());
        
        // Verify that the current user is the recruiter who created this job
        if (!job.getRecruiter().getId().equals(userId)) {
            throw new IllegalStateException("You can only update jobs that you created");
        }

        jobMapper.updateEntityFromRequest(job, request);
        Job updatedJob = jobRepository.save(job);
        
        return jobMapper.toDTO(updatedJob);
    }

    @Transactional
    public void deleteJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job not found with id: " + id));
        
        Long userId = Long.parseLong(SecurityUtils.getCurrentUserId());
        
        // Verify that the current user is the recruiter who created this job
        if (!job.getRecruiter().getId().equals(userId)) {
            throw new IllegalStateException("You can only delete jobs that you created");
        }

        job.setActive(false);
        jobRepository.save(job);
    }
    
    public List<JobDTO> getJobsByRecruiter(Long recruiterId) {
        List<Job> jobs = jobRepository.findByRecruiterIdAndActiveTrue(recruiterId);
        return jobMapper.toDTOList(jobs);
    }
    
    public Page<JobDTO> searchJobs(String keyword, String location, String skillLevel, Pageable pageable) {
        Page<Job> jobs;
        
        if (keyword != null && !keyword.isEmpty()) {
            if (location != null && !location.isEmpty()) {
                if (skillLevel != null && !skillLevel.isEmpty()) {
                    // Search by all criteria
                    jobs = jobRepository.findByTitleContainingOrDescriptionContainingAndLocationContainingAndSkillLevelAndActiveTrue(
                            keyword, keyword, location, skillLevel, pageable);
                } else {
                    // Search by keyword and location
                    jobs = jobRepository.findByTitleContainingOrDescriptionContainingAndLocationContainingAndActiveTrue(
                            keyword, keyword, location, pageable);
                }
            } else if (skillLevel != null && !skillLevel.isEmpty()) {
                // Search by keyword and skill level
                jobs = jobRepository.findByTitleContainingOrDescriptionContainingAndSkillLevelAndActiveTrue(
                        keyword, keyword, skillLevel, pageable);
            } else {
                // Search by keyword only
                jobs = jobRepository.findByTitleContainingOrDescriptionContainingAndActiveTrue(
                        keyword, keyword, pageable);
            }
        } else if (location != null && !location.isEmpty()) {
            if (skillLevel != null && !skillLevel.isEmpty()) {
                // Search by location and skill level
                jobs = jobRepository.findByLocationContainingAndSkillLevelAndActiveTrue(
                        location, skillLevel, pageable);
            } else {
                // Search by location only
                jobs = jobRepository.findByLocationContainingAndActiveTrue(location, pageable);
            }
        } else if (skillLevel != null && !skillLevel.isEmpty()) {
            // Search by skill level only
            jobs = jobRepository.findBySkillLevelAndActiveTrue(skillLevel, pageable);
        } else {
            // No search criteria, return all active jobs
            jobs = jobRepository.findByActiveTrue(pageable);
        }
        
        return jobMapper.toDTOPage(jobs);
    }

    public List<String> generateQuestionsForJob(Long id, int numberOfQuestions) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Job not found with id: " + id));
        
        return aiService.generateQuestions(
            job.getDescription(),
            job.getSkillLevel(),
            numberOfQuestions
        );
    }
}
