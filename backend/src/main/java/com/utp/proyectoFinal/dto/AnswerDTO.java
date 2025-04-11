package com.utp.proyectoFinal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerDTO {
    private Long id;
    private Long questionId;
    private String content;
    private Boolean isCorrect;
    private String explanation;
}
