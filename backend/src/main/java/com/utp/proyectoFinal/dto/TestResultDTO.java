package com.utp.proyectoFinal.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResultDTO {
    private Long id;
    private Long applicantId;
    private TestDTO test;
    private Double score;
    private LocalDateTime completedAt;
    private String aiFeedback;
    private Integer timeTakenSeconds;
}
