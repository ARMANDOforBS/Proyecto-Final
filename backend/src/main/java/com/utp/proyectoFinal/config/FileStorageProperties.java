package com.utp.proyectoFinal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "app.file.storage")
@Data
public class FileStorageProperties {
    private String uploadDir;
}
