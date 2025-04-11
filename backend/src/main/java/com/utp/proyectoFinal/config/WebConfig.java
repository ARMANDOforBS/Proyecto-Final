package com.utp.proyectoFinal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // La configuración CORS ahora se maneja en CorsFilter
}
