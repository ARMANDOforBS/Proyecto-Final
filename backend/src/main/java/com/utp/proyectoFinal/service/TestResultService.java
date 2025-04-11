package com.utp.proyectoFinal.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utp.proyectoFinal.dto.AnswerDTO;
import com.utp.proyectoFinal.dto.TestResultDTO;
import com.utp.proyectoFinal.exception.ResourceNotFoundException;
import com.utp.proyectoFinal.exception.TestAlreadyStartedException;
import com.utp.proyectoFinal.exception.TestAlreadyCompletedException;
import com.utp.proyectoFinal.exception.InvalidTestDataException;
import com.utp.proyectoFinal.exception.TestTimeExpiredException;
import com.utp.proyectoFinal.exception.InvalidAnswerException;
import com.utp.proyectoFinal.model.Answer;
import com.utp.proyectoFinal.model.Applicant;
import com.utp.proyectoFinal.model.ApplicantStatus;
import com.utp.proyectoFinal.model.Question;
import com.utp.proyectoFinal.model.Test;
import com.utp.proyectoFinal.model.TestResult;
import com.utp.proyectoFinal.repository.AnswerRepository;
import com.utp.proyectoFinal.repository.ApplicantRepository;
import com.utp.proyectoFinal.repository.QuestionRepository;
import com.utp.proyectoFinal.repository.TestRepository;
import com.utp.proyectoFinal.repository.TestResultRepository;

import com.utp.proyectoFinal.security.SecurityUtils;
import com.utp.proyectoFinal.model.User;
import com.utp.proyectoFinal.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestResultService {

    private final TestResultRepository testResultRepository;
    private final TestRepository testRepository;
    private final ApplicantRepository applicantRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ModelMapper modelMapper;
    private final AIService aiService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TestResultDTO> getAllTestResults() {
        log.debug("Fetching all test results");
        return testResultRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TestResultDTO getTestResultById(Long id) {
        log.debug("Fetching test result with id: {}", id);
        TestResult testResult = testResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test result not found with id: " + id));
        return convertToDTO(testResult);
    }

    @Transactional(readOnly = true)
    public List<TestResultDTO> getTestResultsByApplicantId(Long applicantId) {
        log.debug("Fetching test results for applicant: {}", applicantId);
        
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found with id: " + applicantId));
        
        return testResultRepository.findByApplicant(applicant).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TestResultDTO> getTestResultsByTestId(Long testId) {
        log.debug("Fetching test results for test: {}", testId);
        
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + testId));
        
        return testResultRepository.findByTest(test).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TestResultDTO> getTestResultsForCurrentUser() {
        log.debug("Fetching test results for current user");
        
        try {
            // Obtener el ID del usuario autenticado
            String currentUsername = SecurityUtils.getCurrentUserId();
            User user = userRepository.findByEmail(currentUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + currentUsername));
            
            Long currentUserId = user.getId();
        
            // Buscar aplicante asociado al usuario actual
            List<Applicant> applicants = applicantRepository.findByUserId(currentUserId);
            if (applicants.isEmpty()) {
                log.warn("No se encontró un perfil de aplicante para el usuario {}", currentUserId);
                return List.of(); // Retornar lista vacía si no hay perfil de aplicante
            }
            
            Applicant applicant = applicants.get(0);
        
            // Obtener los resultados de pruebas para el aplicante
            return testResultRepository.findByApplicant(applicant).stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error al obtener los resultados de pruebas para el usuario actual", e);
            return List.of(); // Retornar lista vacía en caso de error
        }
    }

    @Transactional
    public TestResultDTO startTest(Long applicantId, Long testId) {
        log.info("Starting test {} for applicant {}", testId, applicantId);
        
        try {
            Applicant applicant = applicantRepository.findById(applicantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Applicant not found with id: " + applicantId));
            
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + testId));
            
            // Check if the test is already in progress
            List<TestResult> inProgressTests = testResultRepository.findByApplicantAndTestAndCompletedAtIsNull(applicant, test);
            if (!inProgressTests.isEmpty()) {
                throw new TestAlreadyStartedException("Test is already in progress for this applicant");
            }
            
            // Create new test result
            TestResult testResult = new TestResult();
            testResult.setApplicant(applicant);
            testResult.setTest(test);
            testResult.setStartedAt(LocalDateTime.now());
            
            TestResult savedTestResult = testResultRepository.save(testResult);
            log.info("Successfully started test {} for applicant {}", testId, applicantId);
            
            return convertToDTO(savedTestResult);
        } catch (ResourceNotFoundException | TestAlreadyStartedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error starting test {} for applicant {}: {}", testId, applicantId, e.getMessage(), e);
            throw new RuntimeException("Failed to start test", e);
        }
    }

    @Transactional
    public TestResultDTO submitTestAnswers(Long testResultId, Map<Long, AnswerDTO> answers) {
        log.info("Submitting answers for test result: {}", testResultId);
        
        try {
            TestResult testResult = testResultRepository.findById(testResultId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test result not found with id: " + testResultId));
            
            // Check if test is already completed
            if (testResult.getCompletedAt() != null) {
                throw new TestAlreadyCompletedException("Test is already completed");
            }
            
            // Check if test time has expired
            Test test = testResult.getTest();
            if (test.getDurationMinutes() != null) {
                LocalDateTime expirationTime = testResult.getStartedAt().plusMinutes(test.getDurationMinutes());
                if (LocalDateTime.now().isAfter(expirationTime)) {
                    testResult.setCompletedAt(expirationTime);
                    testResult.setScore(0.0);
                    testResultRepository.save(testResult);
                    throw new TestTimeExpiredException("Test time has expired");
                }
            }
            
            // Process answers
            double totalPoints = 0;
            double earnedPoints = 0;
            
            for (Map.Entry<Long, AnswerDTO> entry : answers.entrySet()) {
                Long questionId = entry.getKey();
                AnswerDTO answerDto = entry.getValue();
                
                Question question = questionRepository.findById(questionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + questionId));
                
                // Validate question belongs to the test
                if (!question.getTest().getId().equals(testResult.getTest().getId())) {
                    throw new InvalidAnswerException("Question does not belong to this test");
                }
                
                totalPoints += question.getPointsValue();
                
                // Create or update answer
                Answer answer = answerRepository.findByTestResultAndQuestion(testResult, question);
                if (answer == null) {
                    answer = new Answer();
                }
                
                answer.setTestResult(testResult);
                answer.setQuestion(question);
                answer.setContent(answerDto.getContent());
                
                // For multiple choice questions
                if (question.getType().equals("MULTIPLE_CHOICE") && answerDto.getIsCorrect() != null) {
                    answer.setIsCorrect(answerDto.getIsCorrect());
                    if (answerDto.getIsCorrect()) {
                        earnedPoints += question.getPointsValue();
                    }
                } 
                // For open-ended questions
                else if (question.getType().equals("OPEN_ENDED")) {
                    // Use AI to evaluate open-ended answers
                    if (answerDto.getContent() != null && !answerDto.getContent().trim().isEmpty()) {
                        try {
                            String prompt = String.format(
                                "Evaluate this answer to the following question: \"%s\"\n\nAnswer: \"%s\"\n\n" +
                                "Rate the answer on a scale from 0.0 to 1.0, where 0.0 is completely incorrect and 1.0 is perfect.\n" +
                                "Return only the score as a decimal number between 0.0 and 1.0.",
                                question.getContent(), answerDto.getContent()
                            );
                            
                            String aiEvaluation = aiService.generateText(prompt, 1024);
                            double aiScore = parseAiScore(aiEvaluation);
                            
                            answer.setExplanation("AI Score: " + aiScore);
                            earnedPoints += question.getPointsValue() * aiScore;
                        } catch (Exception e) {
                            log.error("Error evaluating open-ended answer with AI: {}", e.getMessage());
                            // Default to manual review
                            answer.setExplanation("Pending manual review");
                        }
                    } else {
                        answer.setExplanation("No answer provided");
                    }
                }
                
                answerRepository.save(answer);
            }
            
            // Update test result
            testResult.setCompletedAt(LocalDateTime.now());
            double scorePercentage = totalPoints > 0 ? (earnedPoints / totalPoints) * 100 : 0;
            testResult.setScore(scorePercentage);
            
            // Update applicant status if passed
            if (scorePercentage >= test.getPassingScore()) {
                updateApplicantStatus(testResult.getApplicant());
            }
            
            TestResult savedTestResult = testResultRepository.save(testResult);
            log.info("Successfully submitted answers for test result: {}", testResultId);
            
            return convertToDTO(savedTestResult);
        } catch (ResourceNotFoundException | TestAlreadyCompletedException | TestTimeExpiredException | InvalidAnswerException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error submitting answers for test result {}: {}", testResultId, e.getMessage(), e);
            throw new RuntimeException("Failed to submit test answers", e);
        }
    }

    @Transactional
    public TestResultDTO reviewTestResult(Long testResultId, Map<Long, Boolean> reviewResults) {
        log.info("Reviewing test result: {}", testResultId);
        
        try {
            TestResult testResult = testResultRepository.findById(testResultId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test result not found with id: " + testResultId));
            
            // Check if test is completed
            if (testResult.getCompletedAt() == null) {
                throw new InvalidTestDataException("Test is not completed yet");
            }
            
            // Process review results
            double totalPoints = 0;
            double earnedPoints = 0;
            
            List<Answer> answers = answerRepository.findByTestResult(testResult);
            
            for (Answer answer : answers) {
                Question question = answer.getQuestion();
                totalPoints += question.getPointsValue();
                
                // If the answer is in the review results, update it
                if (reviewResults.containsKey(answer.getId())) {
                    boolean isCorrect = reviewResults.get(answer.getId());
                    answer.setIsCorrect(isCorrect);
                    
                    if (isCorrect) {
                        earnedPoints += question.getPointsValue();
                    }
                    
                    answerRepository.save(answer);
                } else if (answer.getIsCorrect() != null && answer.getIsCorrect()) {
                    // If the answer is already marked as correct
                    earnedPoints += question.getPointsValue();
                } else if (answer.getExplanation() != null && answer.getExplanation().startsWith("AI Score:")) {
                    // If the answer has an AI score in the explanation
                    double aiScore = parseAiScore(answer.getExplanation());
                    earnedPoints += question.getPointsValue() * aiScore;
                }
            }
            
            // Recalculate score
            double scorePercentage = totalPoints > 0 ? (earnedPoints / totalPoints) * 100 : 0;
            testResult.setScore(scorePercentage);
            
            // Update applicant status if passed
            if (scorePercentage >= testResult.getTest().getPassingScore()) {
                updateApplicantStatus(testResult.getApplicant());
            }
            
            TestResult savedTestResult = testResultRepository.save(testResult);
            log.info("Successfully reviewed test result: {}", testResultId);
            
            return convertToDTO(savedTestResult);
        } catch (ResourceNotFoundException | InvalidTestDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error reviewing test result {}: {}", testResultId, e.getMessage(), e);
            throw new RuntimeException("Failed to review test result", e);
        }
    }

    @Transactional
    public void deleteTestResult(Long id) {
        log.info("Attempting to delete test result: {}", id);
        
        try {
            if (!testResultRepository.existsById(id)) {
                throw new ResourceNotFoundException("Test result not found with id: " + id);
            }
            testResultRepository.deleteById(id);
            log.info("Successfully deleted test result: {}", id);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting test result {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete test result", e);
        }
    }

    private void updateApplicantStatus(Applicant applicant) {
        if (applicant.getStatus() == ApplicantStatus.PENDING) {
            applicant.setStatus(ApplicantStatus.TESTING);
            applicantRepository.save(applicant);
        } else if (applicant.getStatus() == ApplicantStatus.TESTING) {
            // Check if all tests are completed
            long completedTests = testResultRepository.countByApplicantAndScoreIsNotNull(applicant);
            long totalTests = testRepository.count();
            
            if (completedTests >= totalTests) {
                applicant.setStatus(ApplicantStatus.INTERVIEW);
                applicantRepository.save(applicant);
            }
        }
    }

    private double parseAiScore(String aiEvaluation) {
        try {
            if (aiEvaluation.startsWith("AI Score: ")) {
                String scoreStr = aiEvaluation.substring(10);
                return Double.parseDouble(scoreStr);
            }
            return 0.5; // Default score if parsing fails
        } catch (Exception e) {
            log.error("Error parsing AI score: {}", e.getMessage());
            return 0.5; // Default score if parsing fails
        }
    }

    private TestResultDTO convertToDTO(TestResult testResult) {
        return modelMapper.map(testResult, TestResultDTO.class);
    }
}
