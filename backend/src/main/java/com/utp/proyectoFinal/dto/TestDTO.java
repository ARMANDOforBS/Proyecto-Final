package com.utp.proyectoFinal.dto;

import java.util.List;

import com.utp.proyectoFinal.model.TestType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestDTO {
    private Long id;
    private String title;
    private String description;
    private Integer timeLimitMinutes;
    private Double passingScore;
    private TestType testType;
    private Integer difficultyLevel;
    private Boolean isActive;
    private List<QuestionDTO> questions;
}
