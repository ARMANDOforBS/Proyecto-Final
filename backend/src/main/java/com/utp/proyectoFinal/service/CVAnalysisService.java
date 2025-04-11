package com.utp.proyectoFinal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.utp.proyectoFinal.exception.AIProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Servicio especializado para el análisis de CVs utilizando la API de Google Gemini
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CVAnalysisService {

    @Value("${gemini.api.url}")
    private String apiUrl;
    
    @Value("${gemini.api.key}")
    private String apiKey;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // Caché en memoria para reducir llamadas a la API
    private final Map<String, CacheEntry<?>> responseCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MINUTES = 60;
    
    /**
     * Clase interna para manejar entradas en caché con tiempo de expiración
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
        CacheEntry<T> entry = (CacheEntry<T>) responseCache.get(cacheKey);
        
        if (entry != null && !entry.isExpired()) {
            log.info("Cache hit for key: {}", cacheKey);
            return entry.getValue();
        }
        
        log.info("Cache miss for key: {}, computing new value", cacheKey);
        T result = supplier.get();
        responseCache.put(cacheKey, new CacheEntry<>(result, CACHE_EXPIRY_MINUTES));
        return result;
    }
    
    /**
     * Analiza un CV utilizando la IA de Gemini y devuelve puntuaciones para diferentes aspectos
     * 
     * @param cvText El texto del CV a analizar
     * @param jobDescription La descripción del trabajo para comparar
     * @return Un mapa con puntuaciones para diferentes aspectos del CV
     */
    @Cacheable(value = "cvAnalysis", key = "#cvText.hashCode() + #jobDescription.hashCode()")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Map<String, Object> analyzeCV(String cvText, String jobDescription) {
        log.info("Analyzing CV with Gemini, CV length: {}", cvText.length());
        String cacheKey = "gemini_cv_analysis_" + (cvText + jobDescription).hashCode();
        
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
                
                // Configurar los parámetros de generación - ajustados para análisis de CV
                ObjectNode generationConfigNode = objectMapper.createObjectNode();
                generationConfigNode.put("temperature", 0.2);  // Temperatura más baja para respuestas más deterministas
                generationConfigNode.put("maxOutputTokens", 2048);
                generationConfigNode.put("topP", 0.95);
                requestBody.set("generationConfig", generationConfigNode);
                
                HttpEntity<String> request = new HttpEntity<>(requestBody.toString(), headers);
                
                try {
                    ResponseEntity<String> response = restTemplate.postForEntity(
                        apiUrl + "?key=" + apiKey, 
                        request, 
                        String.class
                    );
                    
                    if (response.getStatusCode() == HttpStatus.OK) {
                        // Procesar respuesta y extraer puntuaciones y comentarios
                        Map<String, Object> result = new HashMap<>();
                        JsonNode jsonNode = objectMapper.readTree(response.getBody());
                        
                        if (jsonNode.has("candidates") && jsonNode.get("candidates").isArray() && 
                            jsonNode.get("candidates").size() > 0) {
                            
                            JsonNode candidate = jsonNode.get("candidates").get(0);
                            if (candidate.has("content") && candidate.get("content").has("parts") && 
                                candidate.get("content").get("parts").isArray() && 
                                candidate.get("content").get("parts").size() > 0) {
                                
                                String analysis = candidate.get("content").get("parts").get(0).get("text").asText();
                                
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
                                
                                log.info("Successfully analyzed CV with Gemini");
                                return result;
                            }
                        }
                        
                        log.warn("Formato de respuesta inesperado de la API de Gemini");
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("error", "No se pudo analizar el CV correctamente. El servicio de IA devolvió un formato inesperado.");
                        return errorResult;
                    } else {
                        log.error("Error al analizar CV con Gemini. Estado: {}", response.getStatusCode());
                        throw new AIProcessingException("Error al analizar el CV: Servicio respondió con código " + response.getStatusCode());
                    }
                } catch (HttpServerErrorException e) {
                    if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        log.error("Servicio de Gemini no disponible temporalmente: {}", e.getMessage());
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("error", "El servicio de IA no está disponible en este momento. Por favor, inténtelo más tarde.");
                        return errorResult;
                    } else {
                        log.error("Error del servidor de Gemini: {}", e.getMessage());
                        throw new AIProcessingException("Error al conectar con el servicio de IA: " + e.getStatusCode());
                    }
                } catch (ResourceAccessException e) {
                    log.error("Error de conexión con el servicio de Gemini: {}", e.getMessage());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("error", "No se pudo establecer conexión con el servicio de IA. Compruebe su conexión a internet.");
                    return errorResult;
                }
            } catch (Exception e) {
                log.error("Error al analizar CV con Gemini: {}", e.getMessage(), e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Error al analizar el CV: " + e.getMessage());
                return errorResult;
            }
        });
    }
    
    /**
     * Extrae una sección específica de un texto y limpia el formato
     * 
     * @param text El texto completo del análisis
     * @param sectionName El nombre de la sección a extraer (por ejemplo, "Fortalezas")
     * @return El contenido de la sección limpio de caracteres de formato
     */
    private String extractSection(String text, String sectionName) {
        // Buscar patrones como "5. Fortalezas: 0.3**" o "Fortalezas: 0.3"
        Pattern pattern = Pattern.compile(
            "(?:\\d+\\.)?\\s*" + sectionName + "[:\\s]+([0-9]\\.[0-9]+)?\\s*\\**\\s*(.*?)(?=\\d+\\.\\s+[A-Za-zÀ-ÿ]+:|$)", 
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            // Si hay un grupo de puntuación y un grupo de texto, tomamos el texto
            String sectionText = matcher.groupCount() > 1 && matcher.group(2) != null ? 
                                matcher.group(2).trim() : matcher.group(1).trim();
            
            // Limpiar caracteres de formato
            sectionText = sectionText.replaceAll("\\*\\*", "");
            sectionText = sectionText.replaceAll("^\\s*Comentario:\\s*", "");
            
            // Eliminar líneas en blanco al principio y al final
            sectionText = sectionText.replaceAll("^\\s*\\n+", "").replaceAll("\\n+\\s*$", "");
            
            return sectionText;
        }
        
        return "";
    }
    
    /**
     * Extrae una puntuación numérica de un texto de análisis
     * 
     * @param text El texto de donde extraer la puntuación
     * @param category La categoría o clave para buscar la puntuación
     * @return Un valor entre 0 y 1 que representa la puntuación
     */
    private double extractScore(String text, String category) {
        // Patrones comunes para puntuaciones
        Pattern[] patterns = {
            // Busca patrones como "Relevancia: 0.8" o "Relevancia: 8/10"
            Pattern.compile(category + "[:\\s]+([0-9]\\.[0-9]+|[0-9]+/10|[0-9]+\\s*de\\s*10|[0-9]+)", Pattern.CASE_INSENSITIVE),
            // Busca patrones como "La relevancia es 0.8" o "La relevancia es alta (0.8)"
            Pattern.compile(category + ".*?([0-9]\\.[0-9]+|[0-9]+/10|[0-9]+\\s*de\\s*10|[0-9]+)\\)?")
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String scoreStr = matcher.group(1).trim();
                return normalizeScore(scoreStr);
            }
        }
        
        // Si no se encuentra una puntuación explícita, buscar en el texto completo
        return extractScoreFromText(text);
    }
    
    /**
     * Normaliza una puntuación a un valor entre 0 y 1
     */
    private double normalizeScore(String scoreStr) {
        // Convertir formatos como "8/10" o "8 de 10" a decimales
        if (scoreStr.contains("/")) {
            String[] parts = scoreStr.split("/");
            if (parts.length == 2) {
                try {
                    double numerator = Double.parseDouble(parts[0].trim());
                    double denominator = Double.parseDouble(parts[1].trim());
                    return numerator / denominator;
                } catch (NumberFormatException e) {
                    // Ignorar y continuar con otros métodos
                }
            }
        } else if (scoreStr.contains(" de ")) {
            String[] parts = scoreStr.split(" de ");
            if (parts.length == 2) {
                try {
                    double numerator = Double.parseDouble(parts[0].trim());
                    double denominator = Double.parseDouble(parts[1].trim());
                    return numerator / denominator;
                } catch (NumberFormatException e) {
                    // Ignorar y continuar con otros métodos
                }
            }
        }
        
        try {
            double score = Double.parseDouble(scoreStr);
            // Si la puntuación es mayor que 1, asumimos que está en escala de 10
            if (score > 1) {
                return score / 10.0;
            }
            return score;
        } catch (NumberFormatException e) {
            // Si no podemos convertir a número, devolvemos un valor por defecto
            return 0.5;
        }
    }
    
    /**
     * Extrae un número entre 0 y 1 de un texto sin buscar una categoría específica
     * 
     * @param text El texto donde buscar un número
     * @return Un valor entre 0 y 1
     */
    private double extractScoreFromText(String text) {
        // Buscar cualquier número decimal o entero en el texto
        Pattern pattern = Pattern.compile("([0-9]\\.[0-9]+|[0-9]+/10|[0-9]+\\s*de\\s*10|[0-9]+)");
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            String scoreStr = matcher.group(1);
            return normalizeScore(scoreStr);
        }
        
        // Si no se encuentra ningún número, devolver un valor por defecto
        return 0.5;
    }
    
    // Limpia la caché
    public void clearCache() {
        log.info("Limpiando caché del servicio de análisis de CV");
        responseCache.clear();
    }
}
