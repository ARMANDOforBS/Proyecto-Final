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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Servicio para interactuar con la API de Google Gemini
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiService {

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
     * Genera preguntas técnicas utilizando la API de Gemini
     * 
     * @param topic El tema sobre el que generar preguntas
     * @param count Número de preguntas a generar
     * @param difficulty Nivel de dificultad (EASY, MEDIUM, HARD)
     * @param questionType Tipo de pregunta (MULTIPLE_CHOICE, OPEN_ENDED, etc.)
     * @return JSON con las preguntas generadas
     */
    @Cacheable(value = "geminiQuestionGeneration", key = "#topic + #count + #difficulty + #questionType")
    @Retryable(value = {AIProcessingException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String generateQuestions(String topic, int count, String difficulty, String questionType) {
        log.info("Generating {} {} {} questions for topic: {} using Gemini", count, difficulty, questionType, topic);
        String cacheKey = "gemini_questions_" + (topic + count + difficulty + questionType).hashCode();
        
        return getFromCacheOrCompute(cacheKey, () -> {
            try {
                // Crear un prompt específico para Gemini
                String tipoPreguntas = questionType.equals("MULTIPLE_CHOICE") ? "selección múltiple" : 
                                      questionType.equals("OPEN_ENDED") ? "respuesta abierta" : 
                                      questionType.equals("TRUE_FALSE") ? "verdadero/falso" : "mixto";
                
                String dificultad = "media";
                if (difficulty.equals("EASY")) {
                    dificultad = "fácil";
                } else if (difficulty.equals("HARD")) {
                    dificultad = "difícil";
                }
                
                String prompt = String.format(
                    "Eres un experto en %s y vas a generar %d preguntas técnicas de dificultad %s de tipo %s. " +
                    "Estas preguntas serán utilizadas en una plataforma de evaluación técnica para desarrolladores.\n\n" +
                    "Requisitos específicos:\n" +
                    "1. Todas las preguntas DEBEN ser específicas, técnicas y de nivel profesional.\n" +
                    "2. Todas las preguntas y respuestas DEBEN estar completamente en español.\n" +
                    "3. Las preguntas deben evaluar conocimientos prácticos y aplicados, no simplemente teóricos.\n" +
                    "4. Cada pregunta debe incluir una respuesta detallada y técnicamente precisa.\n" +
                    "5. La explicación debe proporcionar contexto técnico adicional sobre la respuesta.\n\n" +
                    "Genera exactamente %d preguntas con este formato JSON preciso para cada pregunta:\n\n" +
                    "```json\n" +
                    "{\n" +
                    "  \"question\": \"[Pregunta técnica específica sobre %s]\",\n" +
                    "  \"answer\": \"[Respuesta técnica detallada]\",\n" +
                    "  \"explanation\": \"[Explicación técnica que profundiza en la respuesta]\"\n" +
                    "}\n" +
                    "```\n\n" +
                    "Asegúrate de que cada objeto JSON sea válido por sí mismo y separado de los demás. " +
                    "No incluyas numeración ni texto adicional fuera del formato JSON especificado.",
                    topic, count, dificultad, tipoPreguntas, count, topic
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
                generationConfigNode.put("temperature", 0.4);
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
                                
                                // Limpieza adicional para asegurar que solo se devuelven objetos JSON válidos
                                generatedText = cleanupGeneratedQuestions(generatedText);
                                
                                return generatedText;
                            }
                        }
                        
                        log.warn("Unexpected response format from Gemini API");
                        throw new AIProcessingException("Formato de respuesta inesperado de la API de Gemini");
                    } else {
                        log.error("Error al generar preguntas con Gemini. Estado: {}", response.getStatusCode());
                        throw new AIProcessingException("Error al generar preguntas: Servicio respondió con código " + response.getStatusCode());
                    }
                } catch (HttpServerErrorException e) {
                    if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                        log.error("Servicio de Gemini no disponible temporalmente: {}", e.getMessage());
                        throw new AIProcessingException("El servicio de Gemini no está disponible en este momento. Por favor, inténtelo más tarde.");
                    } else {
                        log.error("Error del servidor de Gemini: {}", e.getMessage());
                        throw new AIProcessingException("Error al conectar con el servicio de Gemini: " + e.getStatusCode());
                    }
                } catch (ResourceAccessException e) {
                    log.error("Error de conexión con el servicio de Gemini: {}", e.getMessage());
                    throw new AIProcessingException("No se pudo establecer conexión con el servicio de Gemini. Compruebe su conexión a internet.");
                }
            } catch (AIProcessingException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error al generar preguntas con Gemini: {}", e.getMessage(), e);
                throw new AIProcessingException("Error al generar preguntas con Gemini: " + e.getMessage());
            }
        });
    }
    
    /**
     * Limpia el texto generado para extraer solo los objetos JSON válidos
     */
    private String cleanupGeneratedQuestions(String text) {
        log.debug("Limpiando texto generado: {}", text);
        
        // Eliminar texto antes del primer objeto JSON
        text = text.replaceAll("(?s)^.*?(\\{\\s*\"question\")", "{\n  \"question\"");
        
        // Eliminar texto después del último objeto JSON
        text = text.replaceAll("(?s)(\\})[^\\{\\}]*$", "}");
        
        // Eliminar marcadores de código markdown
        text = text.replaceAll("```json", "").replaceAll("```", "");
        
        // Eliminar numeración de preguntas
        text = text.replaceAll("(?m)^\\d+\\.\\s*", "");
        
        // Asegurar que los objetos JSON estén separados correctamente
        text = text.replaceAll("\\}\\s*\\{", "}\n\n{");
        
        // Eliminar espacios en blanco excesivos
        text = text.replaceAll("\\n\\s*\\n", "\n\n");
        
        // Validar que el resultado sea un JSON válido
        try {
            objectMapper.readTree(text);
            log.debug("Texto limpiado y validado como JSON: {}", text);
            return text;
        } catch (Exception e) {
            log.warn("El texto limpiado no es un JSON válido, intentando extraer objetos JSON individuales");
            
            // Si no es un JSON válido, intentar extraer objetos JSON individuales
            StringBuilder validJson = new StringBuilder();
            Pattern pattern = Pattern.compile("\\{[^\\{\\}]*(\\{[^\\{\\}]*\\})*[^\\{\\}]*\\}");
            Matcher matcher = pattern.matcher(text);
            
            boolean first = true;
            while (matcher.find()) {
                String jsonCandidate = matcher.group();
                try {
                    // Verificar que sea un JSON válido
                    objectMapper.readTree(jsonCandidate);
                    if (!first) {
                        validJson.append("\n\n");
                    }
                    validJson.append(jsonCandidate);
                    first = false;
                } catch (Exception ex) {
                    log.debug("Objeto JSON inválido encontrado: {}", jsonCandidate);
                }
            }
            
            if (validJson.length() > 0) {
                log.debug("Objetos JSON extraídos correctamente: {}", validJson);
                return validJson.toString();
            }
            
            // Si todo falla, devolver un JSON vacío
            log.error("No se pudieron extraer objetos JSON válidos del texto generado");
            return "{}";
        }
    }
}
