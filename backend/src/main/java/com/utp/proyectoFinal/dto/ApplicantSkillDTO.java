package com.utp.proyectoFinal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicantSkillDTO {
    private Long id;
    private Long applicantId;
    private String skillName;
    private Integer proficiencyLevel;
    private Boolean isVerified;
    private Boolean aiValidated;
}
