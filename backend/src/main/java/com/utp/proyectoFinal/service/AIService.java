package com.utp.proyectoFinal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.utp.proyectoFinal.exception.AIProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Servicio unificado de IA que utiliza exclusivamente la API de Google Gemini
 * para todas las funcionalidades de inteligencia artificial del sistema.
 */
@Service
@Slf4j
public class AIService {

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Caché en memoria para reducir llamadas a la API
    private Map<String, CacheEntry<?>> responseCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MINUTES = 60; // 1 hora
    
    public AIService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Clase interna para manejar entradas de caché con expiración
     */
    private static class CacheEntry<T> {
        private final T value;
        private final long expiryTime;
        
        public CacheEntry(T value, long expiryTimeMinutes) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + (expiryTimeMinutes * 60 * 1000); // Convertir minutos a milisegundos
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
    private <T> T getFromCacheOrCompute(String cacheKey, Supplier<T> supplier) {
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
     * Genera preguntas técnicas utilizando la API de Gemini
     * 
     * @param jobDescription Descripción del trabajo para el que se generan preguntas
     * @param skillLevel Nivel de habilidad requerido (junior, mid, senior)
     * @param numberOfQuestions Número de preguntas a generar
     * @return Lista de preguntas generadas
     */
    @Cacheable(value = "geminiQuestionGeneration", key = "#jobDescription.hashCode() + #skillLevel + #numberOfQuestions")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<String> generateQuestions(String jobDescription, String skillLevel, int numberOfQuestions) {
        log.info("Generating {} questions for {} level position using Gemini", numberOfQuestions, skillLevel);
        String cacheKey = "gemini_questions_" + (jobDescription + skillLevel + numberOfQuestions).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt específico para Gemini
                String prompt = String.format(
                    "Eres un experto en entrevistas técnicas. Genera %d preguntas técnicas para una posición de nivel %s " +
                    "con la siguiente descripción: %s. \n\n" +
                    "Las preguntas deben ser específicas, técnicas y de nivel profesional. " +
                    "Devuelve solo las preguntas como una lista numerada en español.",
                    numberOfQuestions, skillLevel, jobDescription
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // Construir el cuerpo de la solicitud según la API de Gemini
                ObjectNode requestBody = objectMapper.createObjectNode();
                
                // Crear el array de contents
                ArrayNode contentsArray = objectMapper.createArrayNode();
                
                // Crear el objeto content
                ObjectNode contentNode = objectMapper.createObjectNode();
                contentNode.put("role", "user");
                
                // Crear el array de parts
                ArrayNode partsArray = objectMapper.createArrayNode();
                
                // Crear el objeto part con el texto
                ObjectNode partNode = objectMapper.createObjectNode();
                partNode.put("text", prompt);
                
                // Añadir el part al array de parts
                partsArray.add(partNode);
                
                // Añadir el array de parts al content
                contentNode.set("parts", partsArray);
                
                // Añadir el content al array de contents
                contentsArray.add(contentNode);
                
                // Añadir el array de contents al cuerpo de la solicitud
                requestBody.set("contents", contentsArray);
                
                // Configurar los parámetros de generación
                ObjectNode generationConfigNode = objectMapper.createObjectNode();
                generationConfigNode.put("temperature", 0.7);
                generationConfigNode.put("maxOutputTokens", 1024);
                generationConfigNode.put("topP", 0.95);
                requestBody.set("generationConfig", generationConfigNode);
                
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                ResponseEntity<String> response = restTemplate.postForEntity(
                    geminiApiUrl + "?key=" + geminiApiKey, 
                    request, 
                    String.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Successfully generated questions with Gemini");
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    // Extraer el texto generado de la respuesta de Gemini
                    if (jsonNode.has("candidates") && jsonNode.get("candidates").isArray() && 
                        jsonNode.get("candidates").size() > 0) {
                        
                        JsonNode candidate = jsonNode.get("candidates").get(0);
                        if (candidate.has("content") && candidate.get("content").has("parts") && 
                            candidate.get("content").get("parts").isArray() && 
                            candidate.get("content").get("parts").size() > 0) {
                            
                            String generatedText = candidate.get("content").get("parts").get(0).get("text").asText();
                            return processGeneratedText(generatedText);
                        }
                    }
                    
                    log.warn("Unexpected response format from Gemini API");
                    throw new AIProcessingException("Formato de respuesta inesperado de la API de Gemini");
                } else {
                    log.error("Error al generar preguntas con Gemini. Estado: {}", response.getStatusCode());
                    throw new AIProcessingException("Error al generar preguntas: Servicio respondió con código " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error generating questions: {}", e.getMessage(), e);
                throw new AIProcessingException("Error generating questions: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Procesa el texto generado por Gemini para extraer elementos
     */
    private List<String> processGeneratedText(String text) {
        return Arrays.stream(text.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.toList());
    }
    
    /**
     * Evalúa una respuesta a una pregunta utilizando IA
     * 
     * @param question La pregunta que se realizó
     * @param expectedAnswer La respuesta esperada o correcta (opcional)
     * @param userAnswer La respuesta proporcionada por el candidato
     * @return Un objeto con puntuación y retroalimentación
     */
    @Cacheable(value = "geminiAnswerEvaluation", key = "#question.hashCode() + #expectedAnswer.hashCode() + #userAnswer.hashCode()")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Map<String, Object> evaluateAnswer(String question, String expectedAnswer, String userAnswer) {
        log.info("Evaluating answer for question: {}", question);
        String cacheKey = "gemini_answer_evaluation_" + (question + expectedAnswer + userAnswer).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt específico para Gemini
                String prompt = String.format(
                    "Eres un evaluador experto de respuestas técnicas. Evalúa la siguiente respuesta:\n\n" +
                    "Pregunta: %s\n\n" +
                    "Respuesta del candidato: %s\n\n" +
                    (expectedAnswer != null && !expectedAnswer.isEmpty() ? "Respuesta esperada: %s\n\n" : "") +
                    "Evalúa la respuesta en una escala de 0 a 1, donde 1 es completamente correcta. " +
                    "Proporciona una puntuación numérica precisa y una retroalimentación detallada que explique " +
                    "por qué la respuesta es correcta o incorrecta, y qué podría mejorarse.\n\n" +
                    "Responde con el siguiente formato JSON exacto:\n" +
                    "{\n" +
                    "  \"score\": [puntuación numérica entre 0 y 1],\n" +
                    "  \"feedback\": \"[retroalimentación detallada]\"\n" +
                    "}\n\n" +
                    "No incluyas ningún texto adicional fuera del formato JSON especificado.",
                    question, userAnswer, expectedAnswer
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // Construir el cuerpo de la solicitud según la API de Gemini
                ObjectNode requestBody = objectMapper.createObjectNode();
                
                // Crear el array de contents
                ArrayNode contentsArray = objectMapper.createArrayNode();
                
                // Crear el objeto content
                ObjectNode contentNode = objectMapper.createObjectNode();
                contentNode.put("role", "user");
                
                // Crear el array de parts
                ArrayNode partsArray = objectMapper.createArrayNode();
                
                // Crear el objeto part con el texto
                ObjectNode partNode = objectMapper.createObjectNode();
                partNode.put("text", prompt);
                
                // Añadir el part al array de parts
                partsArray.add(partNode);
                
                // Añadir el array de parts al content
                contentNode.set("parts", partsArray);
                
                // Añadir el content al array de contents
                contentsArray.add(contentNode);
                
                // Añadir el array de contents al cuerpo de la solicitud
                requestBody.set("contents", contentsArray);
                
                // Configurar los parámetros de generación
                ObjectNode generationConfigNode = objectMapper.createObjectNode();
                generationConfigNode.put("temperature", 0.3);
                generationConfigNode.put("maxOutputTokens", 1024);
                generationConfigNode.put("topP", 0.95);
                requestBody.set("generationConfig", generationConfigNode);
                
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                ResponseEntity<String> response = restTemplate.postForEntity(
                    geminiApiUrl + "?key=" + geminiApiKey, 
                    request, 
                    String.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Successfully evaluated answer with Gemini");
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    // Extraer el texto generado de la respuesta de Gemini
                    if (jsonNode.has("candidates") && jsonNode.get("candidates").isArray() && 
                        jsonNode.get("candidates").size() > 0) {
                        
                        JsonNode candidate = jsonNode.get("candidates").get(0);
                        if (candidate.has("content") && candidate.get("content").has("parts") && 
                            candidate.get("content").get("parts").isArray() && 
                            candidate.get("content").get("parts").size() > 0) {
                            
                            String generatedText = candidate.get("content").get("parts").get(0).get("text").asText();
                            
                            // Extraer el JSON de la respuesta
                            Pattern pattern = Pattern.compile("\\{[^\\{\\}]*(\\{[^\\{\\}]*\\})*[^\\{\\}]*\\}");
                            Matcher matcher = pattern.matcher(generatedText);
                            
                            if (matcher.find()) {
                                String jsonText = matcher.group();
                                JsonNode evaluationNode = objectMapper.readTree(jsonText);
                                
                                Map<String, Object> result = new HashMap<>();
                                result.put("score", evaluationNode.has("score") ? 
                                          evaluationNode.get("score").asDouble() : 0.0);
                                result.put("feedback", evaluationNode.has("feedback") ? 
                                          evaluationNode.get("feedback").asText() : "No se pudo extraer retroalimentación");
                                
                                return result;
                            }
                        }
                    }
                    
                    log.warn("Unexpected response format from Gemini API");
                    throw new AIProcessingException("Formato de respuesta inesperado de la API de Gemini");
                } else {
                    log.error("Error al evaluar respuesta con Gemini. Estado: {}", response.getStatusCode());
                    throw new AIProcessingException("Error al evaluar respuesta: Servicio respondió con código " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error al evaluar respuesta con Gemini: {}", e.getMessage(), e);
                throw new AIProcessingException("Error al evaluar respuesta con Gemini: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Versión simplificada para mantener compatibilidad con el código existente
     */
    public double getAnswerScore(String question, String expectedAnswer, String userAnswer) {
        Map<String, Object> evaluation = evaluateAnswer(question, expectedAnswer, userAnswer);
        return (double) evaluation.get("score");
    }
    

    
    /**
     * Genera texto utilizando la API de Gemini
     * 
     * @param prompt El prompt para generar el texto
     * @param maxTokens Número máximo de tokens a generar
     * @return El texto generado
     */
    @Cacheable(value = "geminiTextGeneration", key = "#prompt.hashCode() + #maxTokens")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String generateText(String prompt, int maxTokens) {
        log.info("Generating text with prompt: {}", prompt.substring(0, Math.min(50, prompt.length())) + "...");
        String cacheKey = "gemini_text_generation_" + (prompt + maxTokens).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // Construir el cuerpo de la solicitud según la API de Gemini
                ObjectNode requestBody = objectMapper.createObjectNode();
                
                // Crear el array de contents
                ArrayNode contentsArray = objectMapper.createArrayNode();
                
                // Crear el objeto content
                ObjectNode contentNode = objectMapper.createObjectNode();
                contentNode.put("role", "user");
                
                // Crear el array de parts
                ArrayNode partsArray = objectMapper.createArrayNode();
                
                // Crear el objeto part con el texto
                ObjectNode partNode = objectMapper.createObjectNode();
                partNode.put("text", prompt);
                
                // Añadir el part al array de parts
                partsArray.add(partNode);
                
                // Añadir el array de parts al content
                contentNode.set("parts", partsArray);
                
                // Añadir el content al array de contents
                contentsArray.add(contentNode);
                
                // Añadir el array de contents al cuerpo de la solicitud
                requestBody.set("contents", contentsArray);
                
                // Configurar los parámetros de generación
                ObjectNode generationConfigNode = objectMapper.createObjectNode();
                generationConfigNode.put("temperature", 0.7);
                generationConfigNode.put("maxOutputTokens", maxTokens);
                generationConfigNode.put("topP", 0.95);
                requestBody.set("generationConfig", generationConfigNode);
                
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                ResponseEntity<String> response = restTemplate.postForEntity(
                    geminiApiUrl + "?key=" + geminiApiKey, 
                    request, 
                    String.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Successfully generated text with Gemini");
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    // Extraer el texto generado de la respuesta de Gemini
                    if (jsonNode.has("candidates") && jsonNode.get("candidates").isArray() && 
                        jsonNode.get("candidates").size() > 0) {
                        
                        JsonNode candidate = jsonNode.get("candidates").get(0);
                        if (candidate.has("content") && candidate.get("content").has("parts") && 
                            candidate.get("content").get("parts").isArray() && 
                            candidate.get("content").get("parts").size() > 0) {
                            
                            return candidate.get("content").get("parts").get(0).get("text").asText();
                        }
                    }
                    
                    log.warn("Unexpected response format from Gemini API");
                    throw new AIProcessingException("Formato de respuesta inesperado de la API de Gemini");
                } else {
                    log.error("Error al generar texto con Gemini. Estado: {}", response.getStatusCode());
                    throw new AIProcessingException("Error al generar texto: Servicio respondió con código " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error generating text: {}", e.getMessage(), e);
                throw new AIProcessingException("Error generating text: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Analiza el sentimiento de un texto utilizando la API de Gemini
     * 
     * @param text El texto a analizar
     * @return Un mapa con el sentimiento (positivo, negativo, neutral) y su puntuación
     */
    @Cacheable(value = "geminiSentimentAnalysis", key = "#text.hashCode()")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Map<String, Object> analyzeSentiment(String text) {
        log.info("Analyzing sentiment for text: {}", text.substring(0, Math.min(50, text.length())) + "...");
        String cacheKey = "gemini_sentiment_analysis_" + text.hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt específico para Gemini
                String prompt = String.format(
                    "Analiza el sentimiento del siguiente texto y clasifícalo como positivo, negativo o neutral. " +
                    "Proporciona también una puntuación de sentimiento entre -1 (muy negativo) y 1 (muy positivo), " +
                    "donde 0 es neutral.\n\n" +
                    "Texto: %s\n\n" +
                    "Responde con el siguiente formato JSON exacto:\n" +
                    "{\n" +
                    "  \"sentiment\": \"[positivo, negativo o neutral]\",\n" +
                    "  \"score\": [puntuación entre -1 y 1]\n" +
                    "}\n\n" +
                    "No incluyas ningún texto adicional fuera del formato JSON especificado.",
                    text
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // Construir el cuerpo de la solicitud según la API de Gemini
                ObjectNode requestBody = objectMapper.createObjectNode();
                
                // Crear el array de contents
                ArrayNode contentsArray = objectMapper.createArrayNode();
                
                // Crear el objeto content
                ObjectNode contentNode = objectMapper.createObjectNode();
                contentNode.put("role", "user");
                
                // Crear el array de parts
                ArrayNode partsArray = objectMapper.createArrayNode();
                
                // Crear el objeto part con el texto
                ObjectNode partNode = objectMapper.createObjectNode();
                partNode.put("text", prompt);
                
                // Añadir el part al array de parts
                partsArray.add(partNode);
                
                // Añadir el array de parts al content
                contentNode.set("parts", partsArray);
                
                // Añadir el content al array de contents
                contentsArray.add(contentNode);
                
                // Añadir el array de contents al cuerpo de la solicitud
                requestBody.set("contents", contentsArray);
                
                // Configurar los parámetros de generación
                ObjectNode generationConfigNode = objectMapper.createObjectNode();
                generationConfigNode.put("temperature", 0.2);
                generationConfigNode.put("maxOutputTokens", 256);
                generationConfigNode.put("topP", 0.95);
                requestBody.set("generationConfig", generationConfigNode);
                
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                ResponseEntity<String> response = restTemplate.postForEntity(
                    geminiApiUrl + "?key=" + geminiApiKey, 
                    request, 
                    String.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Successfully analyzed sentiment with Gemini");
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    // Extraer el texto generado de la respuesta de Gemini
                    if (jsonNode.has("candidates") && jsonNode.get("candidates").isArray() && 
                        jsonNode.get("candidates").size() > 0) {
                        
                        JsonNode candidate = jsonNode.get("candidates").get(0);
                        if (candidate.has("content") && candidate.get("content").has("parts") && 
                            candidate.get("content").get("parts").isArray() && 
                            candidate.get("content").get("parts").size() > 0) {
                            
                            String generatedText = candidate.get("content").get("parts").get(0).get("text").asText();
                            
                            // Extraer el JSON de la respuesta
                            Pattern pattern = Pattern.compile("\\{[^\\{\\}]*(\\{[^\\{\\}]*\\})*[^\\{\\}]*\\}");
                            Matcher matcher = pattern.matcher(generatedText);
                            
                            if (matcher.find()) {
                                String jsonText = matcher.group();
                                JsonNode sentimentNode = objectMapper.readTree(jsonText);
                                
                                Map<String, Object> result = new HashMap<>();
                                result.put("sentiment", sentimentNode.has("sentiment") ? 
                                          sentimentNode.get("sentiment").asText() : "neutral");
                                result.put("score", sentimentNode.has("score") ? 
                                          sentimentNode.get("score").asDouble() : 0.0);
                                
                                return result;
                            }
                        }
                    }
                    
                    log.warn("Unexpected response format from Gemini API");
                    throw new AIProcessingException("Formato de respuesta inesperado de la API de Gemini");
                } else {
                    log.error("Error al analizar sentimiento con Gemini. Estado: {}", response.getStatusCode());
                    throw new AIProcessingException("Error al analizar sentimiento: Servicio respondió con código " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error al analizar sentimiento con Gemini: {}", e.getMessage(), e);
                throw new AIProcessingException("Error al analizar sentimiento con Gemini: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Detecta plagio entre dos textos utilizando la API de Gemini
     * 
     * @param originalText El texto original
     * @param comparisonText El texto a comparar
     * @return Un mapa con el porcentaje de similitud y un análisis detallado
     */
    @Cacheable(value = "geminiPlagiarismDetection", key = "#originalText.hashCode() + #comparisonText.hashCode()")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Map<String, Object> detectPlagiarism(String originalText, String comparisonText) {
        log.info("Detecting plagiarism between texts");
        String cacheKey = "gemini_plagiarism_detection_" + (originalText + comparisonText).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt específico para Gemini
                String prompt = String.format(
                    "Analiza los siguientes dos textos y determina si hay plagio o similitud significativa entre ellos. " +
                    "Proporciona un porcentaje de similitud y un análisis detallado.\n\n" +
                    "Texto Original:\n%s\n\n" +
                    "Texto a Comparar:\n%s\n\n" +
                    "Responde con el siguiente formato JSON exacto:\n" +
                    "{\n" +
                    "  \"similarityPercentage\": [porcentaje entre 0 y 100],\n" +
                    "  \"analysis\": \"[análisis detallado de las similitudes y diferencias]\"\n" +
                    "}\n\n" +
                    "No incluyas ningún texto adicional fuera del formato JSON especificado.",
                    originalText, comparisonText
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // Construir el cuerpo de la solicitud según la API de Gemini
                ObjectNode requestBody = objectMapper.createObjectNode();
                
                // Crear el array de contents
                ArrayNode contentsArray = objectMapper.createArrayNode();
                
                // Crear el objeto content
                ObjectNode contentNode = objectMapper.createObjectNode();
                contentNode.put("role", "user");
                
                // Crear el array de parts
                ArrayNode partsArray = objectMapper.createArrayNode();
                
                // Crear el objeto part con el texto
                ObjectNode partNode = objectMapper.createObjectNode();
                partNode.put("text", prompt);
                
                // Añadir el part al array de parts
                partsArray.add(partNode);
                
                // Añadir el array de parts al content
                contentNode.set("parts", partsArray);
                
                // Añadir el content al array de contents
                contentsArray.add(contentNode);
                
                // Añadir el array de contents al cuerpo de la solicitud
                requestBody.set("contents", contentsArray);
                
                // Configurar los parámetros de generación
                ObjectNode generationConfigNode = objectMapper.createObjectNode();
                generationConfigNode.put("temperature", 0.2);
                generationConfigNode.put("maxOutputTokens", 1024);
                generationConfigNode.put("topP", 0.95);
                requestBody.set("generationConfig", generationConfigNode);
                
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                ResponseEntity<String> response = restTemplate.postForEntity(
                    geminiApiUrl + "?key=" + geminiApiKey, 
                    request, 
                    String.class
                );
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    log.info("Successfully detected plagiarism with Gemini");
                    JsonNode jsonNode = objectMapper.readTree(response.getBody());
                    
                    // Extraer el texto generado de la respuesta de Gemini
                    if (jsonNode.has("candidates") && jsonNode.get("candidates").isArray() && 
                        jsonNode.get("candidates").size() > 0) {
                        
                        JsonNode candidate = jsonNode.get("candidates").get(0);
                        if (candidate.has("content") && candidate.get("content").has("parts") && 
                            candidate.get("content").get("parts").isArray() && 
                            candidate.get("content").get("parts").size() > 0) {
                            
                            String generatedText = candidate.get("content").get("parts").get(0).get("text").asText();
                            
                            // Extraer el JSON de la respuesta
                            Pattern pattern = Pattern.compile("\\{[^\\{\\}]*(\\{[^\\{\\}]*\\})*[^\\{\\}]*\\}");
                            Matcher matcher = pattern.matcher(generatedText);
                            
                            if (matcher.find()) {
                                String jsonText = matcher.group();
                                JsonNode plagiarismNode = objectMapper.readTree(jsonText);
                                
                                Map<String, Object> result = new HashMap<>();
                                result.put("similarityPercentage", plagiarismNode.has("similarityPercentage") ? 
                                          plagiarismNode.get("similarityPercentage").asDouble() : 0.0);
                                result.put("analysis", plagiarismNode.has("analysis") ? 
                                          plagiarismNode.get("analysis").asText() : "No se pudo extraer análisis");
                                
                                return result;
                            }
                        }
                    }
                    
                    log.warn("Unexpected response format from Gemini API");
                    throw new AIProcessingException("Formato de respuesta inesperado de la API de Gemini");
                } else {
                    log.error("Error al detectar plagio con Gemini. Estado: {}", response.getStatusCode());
                    throw new AIProcessingException("Error al detectar plagio: Servicio respondió con código " + response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Error al detectar plagio con Gemini: {}", e.getMessage(), e);
                throw new AIProcessingException("Error al detectar plagio con Gemini: " + e.getMessage(), e);
            }
        });
    }
}
