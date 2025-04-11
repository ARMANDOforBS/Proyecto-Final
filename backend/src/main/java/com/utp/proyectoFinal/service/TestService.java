package com.utp.proyectoFinal.service;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.proyectoFinal.dto.QuestionDTO;
import com.utp.proyectoFinal.dto.TestDTO;
import com.utp.proyectoFinal.exception.ResourceNotFoundException;
import com.utp.proyectoFinal.exception.InvalidTestDataException;
import com.utp.proyectoFinal.exception.AIProcessingException;
import com.utp.proyectoFinal.model.Question;
import com.utp.proyectoFinal.model.QuestionType;
import com.utp.proyectoFinal.model.Test;
import com.utp.proyectoFinal.model.TestType;
import com.utp.proyectoFinal.repository.QuestionRepository;
import com.utp.proyectoFinal.repository.TestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestService {

    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final ModelMapper modelMapper;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TestDTO> getAllTests() {
        log.debug("Fetching all tests");
        return testRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TestDTO getTestById(Long id) {
        log.debug("Fetching test with id: {}", id);
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + id));
        return convertToDTO(test);
    }

    @Transactional(readOnly = true)
    public List<TestDTO> getTestsByType(TestType type) {
        log.debug("Fetching tests by type: {}", type);
        return testRepository.findByTestType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TestDTO createTest(TestDTO testDto) {
        log.info("Creating new test with title: {}", testDto.getTitle());
        
        validateTestData(testDto);
        
        try {
            Test test = convertToEntity(testDto);
            test.setIsActive(true);
            Test savedTest = testRepository.save(test);
            
            log.info("Successfully created test with ID: {}", savedTest.getId());
            return convertToDTO(savedTest);
        } catch (Exception e) {
            log.error("Error creating test: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create test", e);
        }
    }

    @Transactional
    public TestDTO updateTest(Long id, TestDTO testDto) {
        log.info("Updating test with ID: {}", id);
        
        validateTestData(testDto);
        
        try {
            Test existingTest = testRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + id));
            
            // Update fields but preserve relationships
            existingTest.setTitle(testDto.getTitle());
            existingTest.setDescription(testDto.getDescription());
            existingTest.setTestType(testDto.getTestType());
            existingTest.setDifficultyLevel(testDto.getDifficultyLevel());
            existingTest.setPassingScore(testDto.getPassingScore());
            existingTest.setTimeLimitMinutes(testDto.getTimeLimitMinutes());
            
            if (testDto.getIsActive() != null) {
                existingTest.setIsActive(testDto.getIsActive());
            }
            
            Test updatedTest = testRepository.save(existingTest);
            log.info("Successfully updated test with ID: {}", id);
            
            return convertToDTO(updatedTest);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating test with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update test", e);
        }
    }

    @Transactional
    public void deleteTest(Long id) {
        log.info("Deleting test with ID: {}", id);
        
        try {
            Test test = testRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + id));
            
            // Check if test has any associated test results before deleting
            // This would require a TestResultRepository dependency
            
            testRepository.delete(test);
            log.info("Successfully deleted test with ID: {}", id);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting test with ID {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete test", e);
        }
    }

    @Transactional
    public TestDTO addQuestionToTest(Long testId, QuestionDTO questionDto) {
        log.info("Adding question to test with ID: {}", testId);
        
        validateQuestionData(questionDto);
        
        try {
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + testId));
            
            Question question = new Question();
            question.setTest(test);
            question.setContent(questionDto.getContent());
            question.setType(questionDto.getType());
            question.setPointsValue(questionDto.getPointsValue());
            
            if (questionDto.getCorrectAnswer() != null) {
                question.setCorrectAnswer(questionDto.getCorrectAnswer());
            }
            
            if (questionDto.getExplanation() != null) {
                question.setExplanation(questionDto.getExplanation());
            }
            
            if (questionDto.getAiGenerated() != null) {
                question.setAiGenerated(questionDto.getAiGenerated());
            } else {
                question.setAiGenerated(false);
            }
            
            test.getQuestions().add(question);
            Test updatedTest = testRepository.save(test);
            
            log.info("Successfully added question to test with ID: {}", testId);
            return convertToDTO(updatedTest);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error adding question to test with ID {}: {}", testId, e.getMessage(), e);
            throw new RuntimeException("Failed to add question to test", e);
        }
    }

    @Transactional
    public TestDTO removeQuestionFromTest(Long testId, Long questionId) {
        log.info("Removing question {} from test with ID: {}", questionId, testId);
        
        try {
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + testId));
            
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + questionId));
            
            // Verify the question belongs to this test
            if (!question.getTest().getId().equals(testId)) {
                throw new InvalidTestDataException("Question does not belong to the specified test");
            }
            
            // Remove the question from the test's questions collection
            test.getQuestions().remove(question);
            
            // Delete the question
            questionRepository.delete(question);
            
            Test updatedTest = testRepository.save(test);
            log.info("Successfully removed question {} from test {}", questionId, testId);
            
            return convertToDTO(updatedTest);
        } catch (ResourceNotFoundException | InvalidTestDataException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error removing question {} from test {}: {}", questionId, testId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove question from test", e);
        }
    }

    /**
     * Genera preguntas de prueba utilizando IA basadas en un tema específico
     * Versión mejorada que genera preguntas únicas para cada iteración
     * 
     * @param testId ID de la prueba a la que añadir las preguntas
     * @param topic Tema sobre el que generar preguntas
     * @param count Número de preguntas a generar
     * @return La prueba actualizada con las nuevas preguntas
     */
    @Transactional
    public TestDTO generateAIQuestions(Long testId, String topic, int count) {
        log.info("Generating {} AI questions for test {} on topic: {}", count, testId, topic);
        
        if (topic == null || topic.trim().isEmpty()) {
            throw new InvalidTestDataException("Topic is required for AI question generation");
        }
        
        if (count <= 0 || count > 10) {
            throw new InvalidTestDataException("Number of questions must be between 1 and 10");
        }
        
        try {
            Test test = testRepository.findById(testId)
                    .orElseThrow(() -> new ResourceNotFoundException("Test not found with id: " + testId));
            
            // Determinar el tipo de pregunta y dificultad basado en el tipo de prueba
            String difficulty = "MEDIUM";
            String questionType = "MIXED";
            
            if (test.getTestType() == TestType.TECHNICAL) {
                questionType = "TECHNICAL";
                if (test.getDifficultyLevel() != null) {
                    if (test.getDifficultyLevel() <= 2) difficulty = "EASY";
                    else if (test.getDifficultyLevel() >= 4) difficulty = "HARD";
                }
            } else if (test.getTestType() == TestType.PERSONALITY) {
                questionType = "PERSONALITY";
            }
            
            // Generar preguntas usando IA con parámetros específicos
            // Crear una descripción de trabajo basada en el tema y tipo de pregunta
            String jobDescription = String.format("Preguntas sobre %s de tipo %s con dificultad %s", 
                                               topic, questionType, difficulty);
            List<String> questionsList = aiService.generateQuestions(jobDescription, difficulty, count);
            
            // Convertir la lista de preguntas a un formato de texto para mantener compatibilidad
            String generatedContent = String.join("\n", questionsList);
            log.debug("Generated content from AI: {}", generatedContent);
            
            // Procesar el contenido generado para extraer preguntas individuales
            List<Map<String, String>> questions = extractQuestions(generatedContent, count);
            
            // Si no se pudieron extraer preguntas del formato JSON, intentar extraerlas del texto
            if (questions.isEmpty()) {
                questions = extractQuestionsFromText(generatedContent, count);
            }
            
            // Si aún no tenemos preguntas, generar preguntas genéricas
            if (questions.isEmpty()) {
                log.warn("Could not parse questions from AI response, generating generic questions");
                for (int i = 0; i < count; i++) {
                    Map<String, String> question = new HashMap<>();
                    question.put("question", "Question about " + topic + " (part " + (i + 1) + ")");
                    question.put("answer", "This is a placeholder answer. Please review and update.");
                    question.put("explanation", "This question was generated as a placeholder due to parsing issues.");
                    questions.add(question);
                }
            }
            
            // Crear preguntas en la base de datos
            for (Map<String, String> questionData : questions) {
                Question question = new Question();
                question.setTest(test);
                question.setContent(questionData.get("question"));
                question.setCorrectAnswer(questionData.getOrDefault("answer", ""));
                question.setExplanation(questionData.getOrDefault("explanation", ""));
                question.setType(determineQuestionType(questionData.get("question")));
                question.setPointsValue(10.0);
                question.setAiGenerated(true);
                
                // Guardar la pregunta y asegurarse de que tenga el testId correcto
                Question savedQuestion = questionRepository.save(question);
                
                // Asegurarse de que la pregunta tenga el testId correcto
                if (savedQuestion.getTest() == null || savedQuestion.getTest().getId() == null) {
                    savedQuestion.setTest(test);
                    savedQuestion = questionRepository.save(savedQuestion);
                }
                
                test.getQuestions().add(savedQuestion);
            }
            
            // Guardar el test actualizado
            Test updatedTest = testRepository.save(test);
            log.info("Successfully generated {} AI questions for test {}", questions.size(), testId);
            
            return convertToDTO(updatedTest);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating AI questions for test {}: {}", testId, e.getMessage(), e);
            throw new AIProcessingException("Failed to generate questions: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extrae preguntas del contenido generado por la IA
     */
    private List<Map<String, String>> extractQuestions(String content, int count) {
        List<Map<String, String>> questions = new ArrayList<>();
        
        try {
            // Intentar analizar como JSON
            if (content.contains("{") && content.contains("}")) {
                if (content.trim().startsWith("[")) {
                    // Es un array JSON
                    JsonNode jsonArray = objectMapper.readTree(content);
                    for (JsonNode item : jsonArray) {
                        Map<String, String> question = extractQuestionFromJsonNode(item);
                        if (question != null && !question.isEmpty()) {
                            questions.add(question);
                            if (questions.size() >= count) break;
                        }
                    }
                } else {
                    // Puede contener múltiples objetos JSON separados
                    // Primero intentamos dividir por objetos JSON completos
                    String[] jsonObjects = content.split("\\}\\s*\\{");
                    
                    if (jsonObjects.length > 1) {
                        // Hay múltiples objetos JSON separados
                        for (int i = 0; i < jsonObjects.length; i++) {
                            String jsonStr = jsonObjects[i];
                            // Añadir llaves faltantes para el primer y último objeto
                            if (i == 0 && !jsonStr.trim().startsWith("{")) {
                                jsonStr = "{" + jsonStr;
                            }
                            if (i == 0) {
                                jsonStr = jsonStr + "}";
                            } else if (i == jsonObjects.length - 1) {
                                jsonStr = "{" + jsonStr;
                            } else {
                                jsonStr = "{" + jsonStr + "}";
                            }
                            
                            try {
                                JsonNode jsonNode = objectMapper.readTree(jsonStr);
                                Map<String, String> question = extractQuestionFromJsonNode(jsonNode);
                                if (question != null && !question.isEmpty()) {
                                    questions.add(question);
                                    if (questions.size() >= count) break;
                                }
                            } catch (Exception e) {
                                log.debug("Error parsing JSON object: {}", e.getMessage());
                            }
                        }
                    } else {
                        // Es un solo objeto JSON
                        JsonNode jsonNode = objectMapper.readTree(content);
                        Map<String, String> question = extractQuestionFromJsonNode(jsonNode);
                        if (question != null && !question.isEmpty()) {
                            questions.add(question);
                        }
                    }
                }
            }
            
            // Intentar extraer objetos JSON del texto si aún no tenemos suficientes preguntas
            if (questions.size() < count) {
                Pattern pattern = Pattern.compile("\\{[^\\{\\}]*(\\{[^\\{\\}]*\\})*[^\\{\\}]*\\}");
                Matcher matcher = pattern.matcher(content);
                while (matcher.find() && questions.size() < count) {
                    try {
                        String jsonStr = matcher.group();
                        JsonNode jsonNode = objectMapper.readTree(jsonStr);
                        Map<String, String> question = extractQuestionFromJsonNode(jsonNode);
                        if (question != null && !question.isEmpty()) {
                            questions.add(question);
                        }
                    } catch (Exception e) {
                        log.debug("Could not parse JSON from match: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing JSON from AI response: {}", e.getMessage());
        }
        
        return questions;
    }
    
    /**
     * Extrae información de pregunta de un nodo JSON
     */
    private Map<String, String> extractQuestionFromJsonNode(JsonNode node) {
        Map<String, String> question = new HashMap<>();
        
        // Buscar campos comunes en diferentes formatos de respuesta con más alternativas
        String[] questionFields = {"question", "pregunta", "text", "content", "prompt", "title", "texto", "contenido"};
        String[] answerFields = {"answer", "respuesta", "correctAnswer", "correct_answer", "solution", "solucion", "respuestaCorrecta"};
        String[] explanationFields = {"explanation", "explicacion", "explicación", "reason", "justification", "feedback", "razon", "razón", "justificacion", "justificación"};
        
        try {
            // Extraer pregunta
            for (String field : questionFields) {
                if (node.has(field) && !node.get(field).asText().isEmpty()) {
                    String text = node.get(field).asText().trim();
                    // Eliminar prefijos comunes que podrían aparecer en las respuestas
                    text = text.replaceAll("^(Pregunta:|Question:|Q:|P:)\\s*", "");
                    
                    // Verificar si el texto es demasiado genérico o es un placeholder
                    if (text.equals("[Pregunta técnica específica sobre React]") || 
                        text.contains("[Pregunta") || 
                        text.length() < 10) {
                        log.debug("Pregunta demasiado genérica o placeholder detectado: {}", text);
                        return null; // No usar esta pregunta
                    }
                    
                    question.put("question", text);
                    break;
                }
            }
            
            // Extraer respuesta
            for (String field : answerFields) {
                if (node.has(field) && !node.get(field).asText().isEmpty()) {
                    String text = node.get(field).asText().trim();
                    // Eliminar prefijos comunes
                    text = text.replaceAll("^(Respuesta:|Answer:|A:|R:)\\s*", "");
                    
                    // Verificar si la respuesta es demasiado genérica o es un placeholder
                    if (text.equals("[Respuesta técnica detallada]") || 
                        text.contains("[Respuesta") || 
                        text.length() < 10) {
                        log.debug("Respuesta demasiado genérica o placeholder detectado: {}", text);
                        return null; // No usar esta pregunta
                    }
                    
                    // Limitar la longitud de la respuesta para evitar respuestas excesivamente largas
                    if (text.length() > 1000) {
                        text = text.substring(0, 997) + "...";
                    }
                    
                    question.put("answer", text);
                    break;
                }
            }
            
            // Extraer explicación
            for (String field : explanationFields) {
                if (node.has(field) && !node.get(field).asText().isEmpty()) {
                    String text = node.get(field).asText().trim();
                    // Eliminar prefijos comunes
                    text = text.replaceAll("^(Explicación:|Explanation:|E:|Exp:)\\s*", "");
                    
                    // Verificar si la explicación es demasiado genérica o es un placeholder
                    if (text.equals("[Explicación técnica que profundiza en la respuesta]") || 
                        text.contains("[Explicación") || 
                        text.length() < 10) {
                        // Si la explicación es genérica, usar un texto estándar en lugar de descartar la pregunta
                        text = "Esta respuesta es correcta según los principios técnicos de " + 
                               "la tecnología en cuestión. Revisa la documentación oficial para más detalles.";
                    }
                    
                    // Limitar la longitud de la explicación
                    if (text.length() > 1000) {
                        text = text.substring(0, 997) + "...";
                    }
                    
                    // Eliminar contenido de otras preguntas que podría haberse mezclado
                    if (text.contains("Pregunta:") || text.contains("Question:")) {
                        int cutoffIndex = text.indexOf("Pregunta:");
                        if (cutoffIndex == -1) cutoffIndex = text.indexOf("Question:");
                        if (cutoffIndex > 10) {
                            text = text.substring(0, cutoffIndex).trim();
                        }
                    }
                    
                    question.put("explanation", text);
                    break;
                }
            }
            
            // Verificar que tengamos al menos pregunta y respuesta
            if (!question.containsKey("question") || !question.containsKey("answer")) {
                log.debug("Falta pregunta o respuesta en el nodo JSON");
                return null;
            }
            
            // Si no hay explicación, añadir una genérica
            if (!question.containsKey("explanation")) {
                question.put("explanation", "Esta respuesta es técnicamente correcta según las mejores prácticas y documentación oficial.");
            }
            
            return question;
        } catch (Exception e) {
            log.warn("Error al extraer información de pregunta: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extrae preguntas del texto cuando no se puede analizar como JSON
     */
    private List<Map<String, String>> extractQuestionsFromText(String content, int count) {
        List<Map<String, String>> questions = new ArrayList<>();
        
        try {
            // Patrones para identificar preguntas numeradas
            Pattern questionPattern = Pattern.compile("(?:^|\\n)\\s*(?:\\d+[.)]\\s*|Q\\d+[.:]\\s*|Question\\s*\\d+[.:]\\s*)(.*?)(?=\\n\\s*(?:\\d+[.)]|Q\\d+[.:]|Question\\s*\\d+[.:]|$))", Pattern.DOTALL);
            Matcher matcher = questionPattern.matcher(content);
            
            while (matcher.find() && questions.size() < count) {
                String questionText = matcher.group(1).trim();
                
                // Buscar respuesta y explicación en el texto de la pregunta
                String answer = "";
                String explanation = "";
                
                // Patrones para identificar respuestas y explicaciones
                Pattern answerPattern = Pattern.compile("(?:Answer|A|Correct Answer)[.:](.*?)(?=\\n|$)", Pattern.CASE_INSENSITIVE);
                Matcher answerMatcher = answerPattern.matcher(questionText);
                if (answerMatcher.find()) {
                    answer = answerMatcher.group(1).trim();
                    // Eliminar la respuesta del texto de la pregunta
                    questionText = questionText.replace(answerMatcher.group(0), "").trim();
                }
                
                Pattern explainPattern = Pattern.compile("(?:Explanation|Reason|Why)[.:](.*?)(?=\\n|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher explainMatcher = explainPattern.matcher(questionText);
                if (explainMatcher.find()) {
                    explanation = explainMatcher.group(1).trim();
                    // Eliminar la explicación del texto de la pregunta
                    questionText = questionText.replace(explainMatcher.group(0), "").trim();
                }
                
                Map<String, String> question = new HashMap<>();
                question.put("question", questionText);
                question.put("answer", answer);
                question.put("explanation", explanation);
                questions.add(question);
            }
            
            // Si no se encontraron preguntas numeradas, dividir el texto en partes iguales
            if (questions.isEmpty() && !content.trim().isEmpty()) {
                String[] parts = content.split("\\n\\s*\\n");
                for (int i = 0; i < Math.min(parts.length, count); i++) {
                    Map<String, String> question = new HashMap<>();
                    question.put("question", parts[i].trim());
                    question.put("answer", "");
                    question.put("explanation", "");
                    questions.add(question);
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting questions from text: {}", e.getMessage());
        }
        
        return questions;
    }
    
    /**
     * Determina el tipo de pregunta basado en su contenido
     */
    private QuestionType determineQuestionType(String questionText) {
        if (questionText == null) {
            return QuestionType.OPEN_ENDED;
        }
        
        String lowerCase = questionText.toLowerCase();
        
        if (lowerCase.contains("true or false") || 
            lowerCase.contains("true/false") || 
            lowerCase.matches(".*\\b(true|false)\\b.*\\?")) {
            return QuestionType.TRUE_FALSE;
        } else if (lowerCase.contains("choose") || 
                  lowerCase.contains("select") || 
                  lowerCase.contains("pick") ||
                  lowerCase.contains("multiple choice") ||
                  lowerCase.matches(".*\\([a-d]\\).*") ||
                  lowerCase.matches(".*\\n\\s*[a-d][.)]\\s+.*")) {
            return QuestionType.MULTIPLE_CHOICE;
        } else {
            return QuestionType.OPEN_ENDED;
        }
    }

    private void validateTestData(TestDTO testDto) {
        if (testDto.getTitle() == null || testDto.getTitle().trim().isEmpty()) {
            throw new InvalidTestDataException("Test title is required");
        }
        
        if (testDto.getTestType() == null) {
            throw new InvalidTestDataException("Test type is required");
        }
        
        if (testDto.getTimeLimitMinutes() != null && testDto.getTimeLimitMinutes() <= 0) {
            throw new InvalidTestDataException("Time limit must be positive");
        }
        
        if (testDto.getPassingScore() != null && (testDto.getPassingScore() < 0 || testDto.getPassingScore() > 100)) {
            throw new InvalidTestDataException("Passing score must be between 0 and 100");
        }
        
        if (testDto.getDifficultyLevel() != null && (testDto.getDifficultyLevel() < 1 || testDto.getDifficultyLevel() > 5)) {
            throw new InvalidTestDataException("Difficulty level must be between 1 and 5");
        }
    }

    private void validateQuestionData(QuestionDTO questionDto) {
        if (questionDto.getContent() == null || questionDto.getContent().trim().isEmpty()) {
            throw new InvalidTestDataException("Question content is required");
        }
        
        if (questionDto.getType() == null) {
            throw new InvalidTestDataException("Question type is required");
        }
        
        if (questionDto.getPointsValue() != null && questionDto.getPointsValue() <= 0) {
            throw new InvalidTestDataException("Points value must be positive");
        }
    }

    private TestDTO convertToDTO(Test test) {
        TestDTO testDTO = modelMapper.map(test, TestDTO.class);
        
        // Asegurar que cada pregunta tenga el testId correcto
        if (testDTO.getQuestions() != null) {
            testDTO.getQuestions().forEach(questionDTO -> {
                questionDTO.setTestId(test.getId());
            });
        }
        
        return testDTO;
    }

    private Test convertToEntity(TestDTO testDto) {
        return modelMapper.map(testDto, Test.class);
    }
}
