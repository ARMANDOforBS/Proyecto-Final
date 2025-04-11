package com.utp.proyectoFinal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.utp.proyectoFinal.model.Question;
import com.utp.proyectoFinal.model.QuestionType;
import com.utp.proyectoFinal.model.Test;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByTest(Test test);
    
    List<Question> findByType(QuestionType type);
    
    @Query("SELECT q FROM Question q WHERE q.aiGenerated = true")
    List<Question> findAiGeneratedQuestions();
    
    @Query("SELECT q FROM Question q WHERE q.test = :test ORDER BY q.pointsValue DESC")
    List<Question> findByTestOrderByPointsValueDesc(Test test);
    
    List<Question> findByTestId(Long testId);
}
