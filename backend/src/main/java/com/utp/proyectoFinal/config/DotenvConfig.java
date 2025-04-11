package com.utp.proyectoFinal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuración para cargar variables de entorno desde un archivo .env
 */
@Configuration
@Slf4j
public class DotenvConfig {

    @PostConstruct
    public void loadEnvironmentVariables() {
        try {
            log.info("Cargando variables de entorno desde archivo .env");
            
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")  // Directorio raíz del proyecto
                    .ignoreIfMissing()  // No fallar si no existe el archivo .env
                    .load();
            
            // Configurar variables de entorno para la base de datos
            setEnvIfPresent(dotenv, "DB_URL");
            setEnvIfPresent(dotenv, "DB_USERNAME");
            setEnvIfPresent(dotenv, "DB_PASSWORD");
            
            // Configurar variables de entorno para Hugging Face API
            setEnvIfPresent(dotenv, "HUGGINGFACE_API_URL");
            setEnvIfPresent(dotenv, "HUGGINGFACE_API_TOKEN");
            
            // Configurar variables de entorno para Google Gemini API
            setEnvIfPresent(dotenv, "GEMINI_API_URL");
            setEnvIfPresent(dotenv, "GEMINI_API_KEY");
            
            // Configurar variables de entorno para JWT
            setEnvIfPresent(dotenv, "JWT_SECRET");
            setEnvIfPresent(dotenv, "JWT_EXPIRATION");
            setEnvIfPresent(dotenv, "JWT_REFRESH_EXPIRATION");
            
            log.info("Variables de entorno cargadas correctamente");
        } catch (Exception e) {
            log.error("Error al cargar variables de entorno: {}", e.getMessage());
        }
    }
    
    private void setEnvIfPresent(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null && !value.isEmpty()) {
            System.setProperty(key, value);
        }
    }
}
