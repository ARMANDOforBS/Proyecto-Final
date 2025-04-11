package com.utp.proyectoFinal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.lang.NonNull;

import com.utp.proyectoFinal.config.FileStorageProperties;
import com.utp.proyectoFinal.exception.FileStorageException;
import com.utp.proyectoFinal.security.SecurityUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties fileStorageProperties;

    @Value("${file.max-size:10485760}") // 10MB in bytes
    private long maxFileSize;

    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public String storeFile(@NonNull MultipartFile file, @NonNull String subDirectory) {
        validateFile(file);

        // Create subdirectory if it doesn't exist
        Path targetLocation = this.fileStorageLocation.resolve(subDirectory);
        try {
            Files.createDirectories(targetLocation);
        } catch (IOException ex) {
            throw new FileStorageException("Could not create the subdirectory.", ex);
        }

        // Generate a unique filename with safe handling of null originalFilename
        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown");
        String safeFilename = StringUtils.cleanPath(originalFilename);
        String fileExtension = getFileExtension(safeFilename);
        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            // Check if the filename contains invalid characters
            if (fileName.contains("..")) {
                throw new FileStorageException("Filename contains invalid path sequence " + fileName);
            }

            // Copy file to the target location
            Path targetPath = targetLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("File {} stored successfully in {}", fileName, subDirectory);
            return subDirectory + "/" + fileName;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
    }

    public String storeCV(@NonNull MultipartFile file) {
        String userId = SecurityUtils.getCurrentUserId();
        return storeFile(file, "cvs/" + userId);
    }

    public String storeProfilePicture(@NonNull MultipartFile file) {
        String userId = SecurityUtils.getCurrentUserId();
        return storeFile(file, "profile-pictures/" + userId);
    }

    private void validateFile(@NonNull MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileStorageException("Failed to store empty file");
        }

        if (file.getSize() > maxFileSize) {
            throw new FileStorageException("File size exceeds maximum limit of 10MB");
        }

        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "unknown");
        String safeFileName = StringUtils.cleanPath(fileName);
        if (safeFileName.contains("..")) {
            throw new FileStorageException("Filename contains invalid path sequence " + fileName);
        }
    }

    private String getFileExtension(@NonNull String fileName) {
        if (fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    public Path getFilePath(@NonNull String relativePath) {
        return this.fileStorageLocation.resolve(relativePath).normalize();
    }

    public void deleteFile(@NonNull String relativePath) {
        try {
            Path filePath = getFilePath(relativePath);
            Files.deleteIfExists(filePath);
            log.info("File {} deleted successfully", relativePath);
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete file " + relativePath, ex);
        }
    }
}
