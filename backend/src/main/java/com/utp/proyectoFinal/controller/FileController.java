package com.utp.proyectoFinal.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.utp.proyectoFinal.exception.ResourceNotFoundException;
import com.utp.proyectoFinal.service.FileStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "File Management", description = "APIs for file downloads")
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{fileType}/{fileName:.+}")
    @Operation(summary = "Download file", description = "Downloads a file by its type and name")
    @PreAuthorize("hasRole('ADMIN') or isAuthenticated()")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileType,
            @PathVariable String fileName) {
        try {
            // Construct the file path
            String filePath = fileType + "/" + fileName;
            Path path = fileStorageService.getFilePath(filePath);
            
            // Check if file exists
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException("File not found: " + filePath);
            }
            
            // Create resource
            Resource resource = new UrlResource(path.toUri());
            
            // Determine content type
            String contentType = Files.probeContentType(path);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            // Return the file
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Error downloading file: " + ex.getMessage());
        }
    }
}
