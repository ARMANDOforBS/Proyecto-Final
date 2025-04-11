package com.utp.proyectoFinal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.utp.proyectoFinal.exception.AIProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
@EnableCaching
public class HuggingFaceService {

    @Value("${huggingface.api.url}")
    private String apiUrl;

    @Value("${huggingface.api.token}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Modelos modernos para 2025
    // Cambiamos el modelo para análisis de CV a un modelo de generación de texto
    private static final String CV_ANALYSIS_MODEL = "meta-llama/Llama-3-8b-instruct";
    private static final String TEXT_GENERATION_MODEL = "meta-llama/Llama-3-8b-instruct";
    private static final String QUESTION_GENERATION_MODEL = "PlanTL-GOB-ES/gpt2-base-bne";
    private static final String SENTIMENT_ANALYSIS_MODEL = "cardiffnlp/twitter-roberta-base-sentiment-latest";
    
    // Caché en memoria para reducir llamadas a la API
    private final Map<String, CacheEntry<?>> responseCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MINUTES = 60;
    
    /**
     * Clase interna para manejar entradas de caché con expiración
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expiryTime;
        
        public CacheEntry(T value, long expiryTimeMinutes) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryTimeMinutes);
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
        
        public T getValue() {
            return value;
        }
    }
    
    /**
     * Obtiene un valor de la caché o ejecuta la función proporcionada
     */
    @SuppressWarnings("unchecked")
    private <T> T getFromCacheOrCompute(String cacheKey, java.util.function.Supplier<T> supplier) {
        CacheEntry<?> entry = responseCache.get(cacheKey);
        
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for key: {}", cacheKey);
            return (T) entry.getValue();
        }
        
        log.debug("Cache miss for key: {}", cacheKey);
        T result = supplier.get();
        responseCache.put(cacheKey, new CacheEntry<>(result, CACHE_EXPIRY_MINUTES));
        return result;
    }

    /**
     * Analiza un CV utilizando IA y devuelve puntuaciones para diferentes aspectos
     * 
     * @param cvText El texto del CV a analizar
     * @param jobDescription La descripción del trabajo para comparar
     * @return Un mapa con puntuaciones para diferentes aspectos del CV
     */
    @Cacheable(value = "cvAnalysis", key = "#cvText.hashCode() + #jobDescription.hashCode()")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Map<String, Object> analyzeCV(String cvText, String jobDescription) {
        log.info("Analyzing CV with length: {}", cvText.length());
        String cacheKey = "cv_analysis_" + (cvText + jobDescription).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt específico para el análisis de CV en español
                String prompt = String.format(
                    "Analiza el siguiente CV para el puesto descrito:\n\n" +
                    "Descripción del puesto: %s\n\n" +
                    "CV del candidato: %s\n\n" +
                    "Evalúa el CV en las siguientes categorías en una escala de 0 a 1, donde 1 es excelente:\n" +
                    "1. Relevancia: ¿Qué tan relevante es la experiencia del candidato para este puesto?\n" +
                    "2. Habilidades técnicas: ¿Las habilidades técnicas del candidato coinciden con los requisitos?\n" +
                    "3. Experiencia: ¿El candidato tiene suficiente experiencia relevante?\n" +
                    "4. Educación: ¿La formación educativa del candidato es adecuada?\n" +
                    "5. Fortalezas: ¿Cuáles son las principales fortalezas del candidato?\n" +
                    "6. Debilidades: ¿Qué aspectos podría mejorar el candidato?\n\n" +
                    "Proporciona una puntuación para cada categoría y un breve comentario. Responde SIEMPRE en español.",
                    jobDescription, cvText
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);
                
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("inputs", prompt);
                ObjectNode parametersNode = objectMapper.createObjectNode();
                parametersNode.put("max_length", 2048);
                // Restauramos el parámetro temperature ya que ahora usamos un modelo de generación de texto
                parametersNode.put("temperature", 0.7);
                requestBody.set("parameters", parametersNode);
                
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(apiUrl + CV_ANALYSIS_MODEL, request, String.class);
                    
                    if (response.getStatusCode() == HttpStatus.OK) {
                        // Procesar respuesta y extraer puntuaciones y comentarios
                        Map<String, Object> result = new HashMap<>();
                        JsonNode jsonNode = objectMapper.readTree(response.getBody());
                        
                        if (jsonNode.isArray() && jsonNode.size() > 0) {
                            String analysis = jsonNode.get(0).get("generated_text").asText();
                            
                            // Extraer puntuaciones del texto generado
                            double relevance = extractScore(analysis, "Relevancia");
                            double technicalSkills = extractScore(analysis, "Habilidades técnicas");
                            double experience = extractScore(analysis, "Experiencia");
                            double education = extractScore(analysis, "Educación");
                            
                            // Extraer fortalezas y debilidades
                            String strengths = extractSection(analysis, "Fortalezas");
                            String weaknesses = extractSection(analysis, "Debilidades");
                            
                            result.put("relevance", relevance);
                            result.put("technicalSkills", technicalSkills);
                            result.put("experience", experience);
                            result.put("education", education);
                            result.put("strengths", strengths);
                            result.put("weaknesses", weaknesses);
                            result.put("fullAnalysis", analysis);
                            
                            log.info("Successfully analyzed CV");
                            return result;
                        }
                        
                        log.warn("Formato de respuesta inesperado de la API");
                        result.put("error", "No se pudo analizar el CV correctamente. El servicio de IA devolvió un formato inesperado.");
                        return result;
                    } else {
                        log.error("Error al analizar CV. Estado: {}", response.getStatusCode());
                        throw new AIProcessingException("Error al analizar el CV: Servicio respondió con código " + response.getStatusCode());
                    }
                } catch (HttpServerErrorException e) {
                    if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        log.error("Servicio de IA no disponible temporalmente: {}", e.getMessage());
                        Map<String, Object> result = new HashMap<>();
                        result.put("error", "El servicio de IA no está disponible en este momento. Por favor, inténtelo más tarde.");
                        return result;
                    } else {
                        log.error("Error del servidor de IA: {}", e.getMessage());
                        throw new AIProcessingException("Error al conectar con el servicio de IA: " + e.getStatusCode());
                    }
                } catch (ResourceAccessException e) {
                    log.error("Error de conexión con el servicio de IA: {}", e.getMessage());
                    Map<String, Object> result = new HashMap<>();
                    result.put("error", "No se pudo establecer conexión con el servicio de IA. Compruebe su conexión a internet.");
                    return result;
                }
            } catch (Exception e) {
                log.error("Error al analizar CV: {}", e.getMessage(), e);
                Map<String, Object> result = new HashMap<>();
                result.put("error", "Error al analizar el CV: " + e.getMessage());
                return result;
            }
        });
    }
    
    /**
     * Extrae una sección específica de un texto
     */
    private String extractSection(String text, String sectionName) {
        try {
            // Buscar patrón como "Fortalezas: texto"
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(sectionName + ": (.*)");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(1).trim();
            }
            
            // Valor por defecto si no se encuentra la sección
            return "";
        } catch (Exception e) {
            log.warn("Error extracting section from text: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Extrae una puntuación numérica de un texto de análisis
     * 
     * @param text El texto de donde extraer la puntuación
     * @param category La categoría o clave para buscar la puntuación
     * @return Un valor entre 0 y 1 que representa la puntuación
     */
    private Double extractScore(String text, String category) {
        try {
            // Buscar patrones como "Relevancia: 0.8" o "Relevancia: 8/10"
            String[] patterns = {
                category + ": ([0-9]\\.[0-9]+)",
                category + ": ([0-9])/10",
                category + " score: ([0-9]\\.[0-9]+)",
                category + ": ([0-9]+)/100"
            };
            
            for (String pattern : patterns) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(text);
                if (m.find()) {
                    String score = m.group(1);
                    
                    // Convertir puntuaciones en diferentes escalas a escala 0-1
                    if (pattern.contains("/10")) {
                        return Double.parseDouble(score) / 10.0;
                    } else if (pattern.contains("/100")) {
                        return Double.parseDouble(score) / 100.0;
                    } else {
                        return Double.parseDouble(score);
                    }
                }
            }
            
            // Valor por defecto si no se encuentra puntuación
            return 0.5;
        } catch (Exception e) {
            log.warn("Error extracting score for {}: {}", category, e.getMessage());
            return 0.5;
        }
    }
    
    /**
     * Extrae un número entre 0 y 1 de un texto sin buscar una categoría específica
     * 
     * @param text El texto donde buscar un número
     * @return Un valor entre 0 y 1
     */
    private Double extractScoreFromText(String text) {
        try {
            // Buscar cualquier número entre 0 y 1 en el texto
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("([0-9]\\.[0-9]+)");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                double score = Double.parseDouble(m.group(1));
                if (score >= 0 && score <= 1) {
                    return score;
                }
            }
            
            // Buscar números del 1 al 10 seguidos de "/10"
            p = java.util.regex.Pattern.compile("([0-9]|10)/10");
            m = p.matcher(text);
            if (m.find()) {
                String scoreStr = m.group(1);
                return Double.parseDouble(scoreStr) / 10.0;
            }
            
            // Valor por defecto
            return 0.5;
        } catch (Exception e) {
            log.warn("Error extracting score from text: {}", e.getMessage());
            return 0.5;
        }
    }

    /**
     * Genera preguntas de prueba basadas en una habilidad o tema
     * Utiliza un modelo de generación de texto más moderno y especializado
     * 
     * @param topic El tema o habilidad sobre el que generar preguntas
     * @param count Número de preguntas a generar
     * @param difficulty Nivel de dificultad (EASY, MEDIUM, HARD)
     * @param questionType Tipo de pregunta (MULTIPLE_CHOICE, OPEN_ENDED, etc.)
     * @return Lista de preguntas generadas
     */
    @Cacheable(value = "questionGeneration", key = "#topic + #count + #difficulty + #questionType")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String generateQuestions(String topic, int count, String difficulty, String questionType) {
        log.info("Generating {} {} {} questions for topic: {}", count, difficulty, questionType, topic);
        String cacheKey = "questions_" + (topic + count + difficulty + questionType).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt adaptado específicamente para el modelo T5-XL
                String tipoPreguntas = questionType.equals("MULTIPLE_CHOICE") ? "selección múltiple" : 
                                      questionType.equals("OPEN_ENDED") ? "respuesta abierta" : 
                                      questionType.equals("TRUE_FALSE") ? "verdadero/falso" : "mixto";
                
                String prompt = String.format(
                    "Genera %d preguntas técnicas específicas en español sobre %s con nivel de dificultad %s de tipo %s. " +
                    "Formato JSON: {\"question\": \"[Pregunta técnica]\", \"answer\": \"[Respuesta técnica detallada]\", \"explanation\": \"[Explicación técnica]\"}. " +
                    "Cada pregunta debe ser específica, desafiante y evaluar conocimientos técnicos avanzados. " +
                    "Todo el contenido DEBE estar solo en español. " +
                    "Las preguntas deben evaluar habilidades prácticas, no conceptos básicos. " +
                    "Las respuestas deben ser técnicamente precisas y detalladas, conteniendo como mínimo 3 líneas.",
                    count, 
                    topic, 
                    getDifficultyInSpanish(difficulty),
                    tipoPreguntas
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("inputs", prompt);
                ObjectNode parametersNode = objectMapper.createObjectNode();
                // Parámetros optimizados para T5-XL
                parametersNode.put("max_length", 2048);
                parametersNode.put("temperature", 0.3);  // Menor temperatura para más precisión
                parametersNode.put("num_return_sequences", 1);
                parametersNode.put("do_sample", true);
                parametersNode.put("top_p", 0.9);
                requestBody.set("parameters", parametersNode);

                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(apiUrl + QUESTION_GENERATION_MODEL, request, String.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        log.info("Successfully generated questions");
                        JsonNode jsonNode = objectMapper.readTree(response.getBody());
                        
                        if (jsonNode.isArray() && jsonNode.size() > 0) {
                            String generatedText = jsonNode.get(0).get("generated_text").asText();
                            
                            // Limpieza adicional para asegurar que solo se devuelven objetos JSON válidos
                            generatedText = cleanupGeneratedQuestions(generatedText);
                            
                            return generatedText;
                        }
                        
                        return response.getBody();
                    } else {
                        log.error("Error al generar preguntas. Estado: {}", response.getStatusCode());
                        throw new AIProcessingException("Error al generar preguntas: Servicio respondió con código " + response.getStatusCode());
                    }
                } catch (HttpServerErrorException e) {
                    if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        log.error("Servicio de IA no disponible temporalmente: {}", e.getMessage());
                        throw new AIProcessingException("El servicio de IA no está disponible en este momento. Por favor, inténtelo más tarde.");
                    } else {
                        log.error("Error del servidor de IA: {}", e.getMessage());
                        throw new AIProcessingException("Error al conectar con el servicio de IA: " + e.getStatusCode());
                    }
                } catch (ResourceAccessException e) {
                    log.error("Error de conexión con el servicio de IA: {}", e.getMessage());
                    throw new AIProcessingException("No se pudo establecer conexión con el servicio de IA. Compruebe su conexión a internet.");
                }
            } catch (AIProcessingException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error al generar preguntas: {}", e.getMessage(), e);
                throw new AIProcessingException("Error al generar preguntas: " + e.getMessage());
            }
        });
    }
    
    /**
     * Limpia el texto generado para asegurar que solo contiene objetos JSON válidos
     */
    private String cleanupGeneratedQuestions(String generatedText) {
        // Eliminar texto introductorio común antes del primer {
        int firstBrace = generatedText.indexOf('{');
        if (firstBrace > 0) {
            generatedText = generatedText.substring(firstBrace);
        }
        
        // Eliminar marcadores de código de markdown como ```json o ```
        generatedText = generatedText.replaceAll("```json", "").replaceAll("```", "");
        
        // Eliminar números de secuencia como "1.", "2.", etc.
        generatedText = generatedText.replaceAll("\\d+\\. \\{", "{");
        
        // Limpiar espacios en blanco excesivos
        generatedText = generatedText.trim();
        
        return generatedText;
    }
    
    /**
     * Versión simplificada para mantener compatibilidad con el código existente
     */
    public String generateQuestions(String topic, int count) {
        return generateQuestions(topic, count, "MEDIUM", "MIXED");
    }

    /**
     * Convierte el nivel de dificultad a español para los prompts
     */
    private String getDifficultyInSpanish(String difficulty) {
        if (difficulty == null) return "de dificultad media";
        
        switch (difficulty.toUpperCase()) {
            case "EASY":
                return "fáciles";
            case "HARD":
                return "difíciles";
            case "MEDIUM":
            default:
                return "de dificultad media";
        }
    }

    /**
     * Evalúa una respuesta a una pregunta utilizando IA
     * 
     * @param question La pregunta que se realizó
     * @param answer La respuesta proporcionada por el candidato
     * @param expectedAnswer La respuesta esperada o correcta (opcional)
     * @return Un objeto con puntuación y retroalimentación
     */
    @Cacheable(value = "answerEvaluation", key = "#question.hashCode() + #answer.hashCode() + #expectedAnswer.hashCode()")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Map<String, Object> evaluateAnswer(String question, String answer, String expectedAnswer) {
        log.info("Evaluating answer for question: {}", question);
        String cacheKey = "eval_" + (question + answer + expectedAnswer).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt más específico para la evaluación de respuestas
                String prompt = String.format(
                    "Pregunta: %s\n\n" +
                    "Respuesta del candidato: %s\n\n" +
                    (expectedAnswer != null && !expectedAnswer.isEmpty() ? "Respuesta esperada: " + expectedAnswer + "\n\n" : "") +
                    "Evalúa la respuesta del candidato en una escala de 0 a 1, donde 1 es perfecta. " +
                    "Considera la corrección, integridad y claridad. " +
                    "Proporciona una puntuación y una breve retroalimentación explicando la evaluación. " +
                    "Responde SIEMPRE en español.",
                    question, answer
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("inputs", prompt);
                ObjectNode parametersNode = objectMapper.createObjectNode();
                parametersNode.put("max_length", 1024);
                parametersNode.put("temperature", 0.5);
                parametersNode.put("num_return_sequences", 1);
                requestBody.set("parameters", parametersNode);

                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(apiUrl + CV_ANALYSIS_MODEL, request, String.class);

                    if (response.getStatusCode() == HttpStatus.OK) {
                        // Procesar respuesta y extraer puntuación y retroalimentación
                        Map<String, Object> result = new HashMap<>();
                        JsonNode jsonNode = objectMapper.readTree(response.getBody());
                        
                        if (jsonNode.isArray() && jsonNode.size() > 0) {
                            String evaluation = jsonNode.get(0).get("generated_text").asText();
                            
                            // Extraer puntuación del texto generado
                            double score = extractScore(evaluation, "Puntuación");
                            if (score == 0.5) { // Si no se encontró "Puntuación", buscar un número entre 0 y 1
                                score = extractScoreFromText(evaluation);
                            }
                            
                            result.put("score", score);
                            result.put("feedback", evaluation);
                            
                            log.info("Successfully evaluated answer with score: {}", score);
                            return result;
                        }
                        
                        log.warn("Formato de respuesta inesperado de la API");
                        result.put("score", 0.5);
                        result.put("feedback", "No se pudo evaluar la respuesta correctamente. El servicio de IA devolvió un formato inesperado.");
                        return result;
                    } else {
                        log.error("Error al evaluar respuesta. Estado: {}", response.getStatusCode());
                        throw new AIProcessingException("Error al evaluar la respuesta: Servicio respondió con código " + response.getStatusCode());
                    }
                } catch (HttpServerErrorException e) {
                    if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        log.error("Servicio de IA no disponible temporalmente: {}", e.getMessage());
                        Map<String, Object> result = new HashMap<>();
                        result.put("score", 0.5);
                        result.put("feedback", "El servicio de IA no está disponible en este momento. Por favor, inténtelo más tarde.");
                        return result;
                    } else {
                        log.error("Error del servidor de IA: {}", e.getMessage());
                        throw new AIProcessingException("Error al conectar con el servicio de IA: " + e.getStatusCode());
                    }
                } catch (ResourceAccessException e) {
                    log.error("Error de conexión con el servicio de IA: {}", e.getMessage());
                    Map<String, Object> result = new HashMap<>();
                    result.put("score", 0.5);
                    result.put("feedback", "No se pudo establecer conexión con el servicio de IA. Compruebe su conexión a internet.");
                    return result;
                }
            } catch (Exception e) {
                log.error("Error al evaluar respuesta: {}", e.getMessage(), e);
                Map<String, Object> result = new HashMap<>();
                result.put("score", 0.5);
                result.put("feedback", "Error al evaluar la respuesta: " + e.getMessage());
                return result;
            }
        });
    }
    
    /**
     * Versión simplificada para mantener compatibilidad con el código existente
     */
    public String evaluateAnswer(String question, String answer) {
        Map<String, Object> evaluation = evaluateAnswer(question, answer, null);
        return evaluation.get("feedback").toString();
    }
    
    /**
     * Genera texto basado en un prompt utilizando IA
     * 
     * @param prompt El prompt para generar texto
     * @param maxLength Longitud máxima del texto generado
     * @param temperature Temperatura para la generación (0.0-1.0)
     * @return Texto generado
     */
    @Cacheable(value = "textGeneration", key = "#prompt.hashCode() + #maxLength + #temperature")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String generateText(String prompt, int maxLength, double temperature) {
        log.info("Generating text for prompt with {} max length", maxLength);
        String cacheKey = "text_" + (prompt + maxLength + temperature).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("inputs", prompt);
                ObjectNode parametersNode = objectMapper.createObjectNode();
                parametersNode.put("max_length", maxLength);
                parametersNode.put("temperature", temperature);
                parametersNode.put("num_return_sequences", 1);
                requestBody.set("parameters", parametersNode);

                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl + TEXT_GENERATION_MODEL, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    // Procesar respuesta y extraer texto generado
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    if (jsonNode.isArray() && jsonNode.size() > 0) {
                        String generatedText = jsonNode.get(0).get("generated_text").asText();
                        log.info("Successfully generated text");
                        return generatedText;
                    }
                    
                    log.warn("Unexpected response format from API");
                    return "Failed to generate text due to unexpected API response format";
                } else {
                    log.error("Failed to generate text. Status: {}", response.getStatusCode());
                    throw new AIProcessingException("Failed to generate text");
                }
            } catch (Exception e) {
                log.error("Error generating text: {}", e.getMessage(), e);
                throw new AIProcessingException("Error generating text", e);
            }
        });
    }
    
    /**
     * Versión simplificada para mantener compatibilidad con el código existente
     */
    public String generateText(String prompt) {
        return generateText(prompt, 1024, 0.7);
    }
    
    /**
     * Analiza el sentimiento de un texto
     * 
     * @param text Texto a analizar
     * @return Mapa con puntuaciones de sentimiento (positivo, negativo, neutral)
     */
    @Cacheable(value = "sentimentAnalysis", key = "#text.hashCode()")
    public Map<String, Double> analyzeSentiment(String text) {
        log.info("Analyzing sentiment of text");
        String cacheKey = "sentiment_" + text.hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);

                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("inputs", text);

                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl + SENTIMENT_ANALYSIS_MODEL, request, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    // Procesar respuesta y extraer puntuaciones de sentimiento
                    Map<String, Double> result = new HashMap<>();
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    if (jsonNode.isArray() && jsonNode.size() > 0) {
                        JsonNode scoresNode = jsonNode.get(0);
                        
                        result.put("positive", findScoreByLabel(scoresNode, "positive"));
                        result.put("negative", findScoreByLabel(scoresNode, "negative"));
                        result.put("neutral", findScoreByLabel(scoresNode, "neutral"));
                        
                        log.info("Successfully analyzed sentiment");
                        return result;
                    }
                    
                    log.warn("Unexpected response format from API");
                    result.put("positive", 0.33);
                    result.put("negative", 0.33);
                    result.put("neutral", 0.34);
                    return result;
                } else {
                    log.error("Failed to analyze sentiment. Status: {}", response.getStatusCode());
                    throw new AIProcessingException("Failed to analyze sentiment");
                }
            } catch (Exception e) {
                log.error("Error analyzing sentiment: {}", e.getMessage(), e);
                throw new AIProcessingException("Error analyzing sentiment", e);
            }
        });
    }
    
    /**
     * Encuentra la puntuación para una etiqueta específica en la respuesta de análisis de sentimiento
     */
    private Double findScoreByLabel(JsonNode scoresNode, String label) {
        for (JsonNode scoreNode : scoresNode) {
            if (scoreNode.has("label") && scoreNode.get("label").asText().equalsIgnoreCase(label)) {
                return scoreNode.get("score").asDouble();
            }
        }
        return 0.0;
    }
    
    /**
     * Detecta plagio en una respuesta comparándola con una base de conocimientos
     * 
     * @param answer Respuesta a verificar
     * @param knowledgeBase Base de conocimientos para comparar
     * @return Puntuación de similitud (0-1) y fragmentos potencialmente plagiados
     */
    public Map<String, Object> detectPlagiarism(String answer, String[] knowledgeBase) {
        log.info("Checking for plagiarism in answer");
        
        Map<String, Object> result = new HashMap<>();
        result.put("plagiarismScore", 0.0);
        result.put("suspiciousFragments", new String[0]);
        
        try {
            // Implementación simplificada de detección de plagio
            // En una implementación real, se utilizaría un algoritmo más sofisticado
            double highestSimilarity = 0.0;
            String mostSimilarSource = "";
            
            for (String source : knowledgeBase) {
                double similarity = calculateSimilarity(answer, source);
                if (similarity > highestSimilarity) {
                    highestSimilarity = similarity;
                    mostSimilarSource = source;
                }
            }
            
            result.put("plagiarismScore", highestSimilarity);
            
            if (highestSimilarity > 0.7) {
                result.put("suspiciousFragments", new String[]{mostSimilarSource});
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error detecting plagiarism: {}", e.getMessage(), e);
            return result;
        }
    }
    
    /**
     * Calcula la similitud entre dos textos (implementación simplificada)
     */
    private double calculateSimilarity(String text1, String text2) {
        // Esta es una implementación muy simplificada
        // En un sistema real, se utilizaría un algoritmo más sofisticado
        
        // Convertir a minúsculas y dividir en palabras
        String[] words1 = text1.toLowerCase().split("\\W+");
        String[] words2 = text2.toLowerCase().split("\\W+");
        
        // Contar palabras comunes
        int commonWords = 0;
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (word1.equals(word2) && word1.length() > 3) {
                    commonWords++;
                    break;
                }
            }
        }
        
        // Calcular similitud como proporción de palabras comunes
        return (double) commonWords / Math.max(words1.length, words2.length);
    }
    
    /**
     * Limpia la caché
     */
    public void clearCache() {
        log.info("Clearing AI service cache");
        responseCache.clear();
    }
}
