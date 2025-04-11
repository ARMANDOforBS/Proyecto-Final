package com.utp.proyectoFinal.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.utp.proyectoFinal.model.Answer;
import com.utp.proyectoFinal.model.Question;
import com.utp.proyectoFinal.model.TestResult;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByQuestion(Question question);
    
    @Query("SELECT a FROM Answer a WHERE a.question = :question AND a.isCorrect = true")
    List<Answer> findCorrectAnswersByQuestion(Question question);
    
    @Query("SELECT a FROM Answer a WHERE a.question.test.id = :testId")
    List<Answer> findByQuestionTestId(Long testId);
    
    /**
     * Finds an answer by test result and question
     * @param testResult the test result
     * @param question the question
     * @return the answer if found
     */
    Answer findByTestResultAndQuestion(TestResult testResult, Question question);
    
    /**
     * Finds all answers for a specific test result
     * @param testResult the test result
     * @return list of answers
     */
    List<Answer> findByTestResult(TestResult testResult);
}
