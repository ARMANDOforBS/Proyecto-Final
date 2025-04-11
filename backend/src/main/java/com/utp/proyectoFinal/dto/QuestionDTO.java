package com.utp.proyectoFinal.dto;

import java.util.List;

import com.utp.proyectoFinal.model.QuestionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDTO {
    private Long id;
    private Long testId;
    private String content;
    private String correctAnswer;
    private String explanation;
    private QuestionType type;
    private Double pointsValue;
    private Boolean aiGenerated;
    private List<AnswerDTO> answers;
}
