package com.utp.proyectoFinal.mapper;

import com.utp.proyectoFinal.dto.JobDTO;
import com.utp.proyectoFinal.dto.JobRequest;
import com.utp.proyectoFinal.model.Job;
import com.utp.proyectoFinal.model.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class JobMapper {

    public JobDTO toDTO(Job job) {
        if (job == null) {
            return null;
        }
        
        return JobDTO.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .skillLevel(job.getSkillLevel())
                .location(job.getLocation())
                .salaryRange(job.getSalaryRange())
                .requiredExperience(job.getRequiredExperience())
                .employmentType(job.getEmploymentType())
                .requiredSkills(job.getRequiredSkills())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .closingDate(job.getClosingDate())
                .active(job.isActive())
                .recruiterId(job.getRecruiter().getId())
                .recruiterName(job.getRecruiter().getFirstName() + " " + job.getRecruiter().getLastName())
                .build();
    }

    public List<JobDTO> toDTOList(List<Job> jobs) {
        return jobs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public Page<JobDTO> toDTOPage(Page<Job> jobs) {
        return jobs.map(this::toDTO);
    }

    public Job toEntity(JobRequest request, User recruiter) {
        if (request == null) {
            return null;
        }
        
        return Job.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .skillLevel(request.getSkillLevel())
                .location(request.getLocation())
                .salaryRange(request.getSalaryRange())
                .requiredExperience(request.getRequiredExperience())
                .employmentType(request.getEmploymentType())
                .requiredSkills(request.getRequiredSkills())
                .closingDate(request.getClosingDate())
                .recruiter(recruiter)
                .build();
    }

    public void updateEntityFromRequest(Job job, JobRequest request) {
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setSkillLevel(request.getSkillLevel());
        job.setLocation(request.getLocation());
        job.setSalaryRange(request.getSalaryRange());
        job.setRequiredExperience(request.getRequiredExperience());
        job.setEmploymentType(request.getEmploymentType());
        job.setRequiredSkills(request.getRequiredSkills());
        job.setClosingDate(request.getClosingDate());
    }
}
