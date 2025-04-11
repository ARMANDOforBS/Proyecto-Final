package com.utp.proyectoFinal.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.springframework.lang.NonNull;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.utp.proyectoFinal.dto.ApplicantDTO;
import com.utp.proyectoFinal.dto.ApplicantRequest;
import com.utp.proyectoFinal.exception.DuplicateProfileException;
import com.utp.proyectoFinal.exception.EntityNotFoundException;
import com.utp.proyectoFinal.exception.InvalidApplicantDataException;
import com.utp.proyectoFinal.exception.InvalidFileException;
import com.utp.proyectoFinal.exception.ResourceNotFoundException;
import com.utp.proyectoFinal.model.Applicant;
import com.utp.proyectoFinal.model.ApplicantStatus;
import com.utp.proyectoFinal.model.Job;
import com.utp.proyectoFinal.model.User;
import com.utp.proyectoFinal.repository.ApplicantRepository;
import com.utp.proyectoFinal.repository.JobRepository;
import com.utp.proyectoFinal.repository.UserRepository;
import com.utp.proyectoFinal.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ModelMapper modelMapper;
    private final FileStorageService fileStorageService;
    private final CVAnalysisService cvAnalysisService;
    private final DocumentTextExtractorService documentTextExtractorService;

    private static final List<String> ALLOWED_CV_TYPES = Arrays.asList(
        "application/pdf", 
        "application/msword", 
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
        "image/jpeg", 
        "image/png"
    );
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Transactional(readOnly = true)
    public List<ApplicantDTO> getAllApplicants() {
        log.debug("Fetching all applicants");
        return applicantRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApplicantDTO getApplicantById(Long id) {
        log.debug("Fetching applicant with id: {}", id);
        Applicant applicant = applicantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found with id: " + id));
        return convertToDto(applicant);
    }

    @Transactional(readOnly = true)
    public ApplicantDTO getApplicantByUserId(Long userId) {
        log.debug("Fetching applicant for user id: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        Applicant applicant = applicantRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found for user with id: " + userId));
        
        return convertToDto(applicant);
    }

    @Transactional
    public ApplicantDTO createApplicant(ApplicantDTO applicantDto, Long userId) {
        log.info("Creating new applicant for user ID: {}", userId);
        
        // Validar que el usuario no tenga ya un perfil de aplicante
        if (applicantRepository.existsByUserId(userId)) {
            log.warn("Attempt to create duplicate applicant profile for user: {}", userId);
            throw new DuplicateProfileException("User already has an applicant profile");
        }

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

            validateApplicantData(applicantDto);
            
            Applicant applicant = convertToEntity(applicantDto);
            applicant.setUser(user);
            applicant.setStatus(ApplicantStatus.PENDING);
            applicant.setCreatedAt(LocalDateTime.now());
            
            Applicant savedApplicant = applicantRepository.save(applicant);
            log.info("Successfully created applicant with ID: {}", savedApplicant.getId());
            
            return convertToDto(savedApplicant);
        } catch (Exception e) {
            log.error("Error creating applicant for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to create applicant profile", e);
        }
    }

    @Transactional
    public ApplicantDTO updateApplicant(Long id, ApplicantDTO applicantDto) {
        log.info("Updating applicant with ID: {}", id);
        
        try {
            Applicant existingApplicant = applicantRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Applicant not found with id: " + id));
            
            validateApplicantData(applicantDto);
            
            // Update fields but preserve relationships
            existingApplicant.setDateOfBirth(applicantDto.getDateOfBirth());
            existingApplicant.setPhoneNumber(applicantDto.getPhoneNumber());
            existingApplicant.setAddress(applicantDto.getAddress());
            existingApplicant.setEducation(applicantDto.getEducation());
            existingApplicant.setSkills(applicantDto.getSkills());
            existingApplicant.setExperience(applicantDto.getExperience());
            existingApplicant.setLastUpdated(LocalDateTime.now());
            
            if (applicantDto.getStatus() != null) {
                existingApplicant.setStatus(applicantDto.getStatus());
            }
            
            Applicant updatedApplicant = applicantRepository.save(existingApplicant);
            log.info("Successfully updated applicant with ID: {}", id);
            
            return convertToDto(updatedApplicant);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating applicant {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to update applicant", e);
        }
    }

    @Transactional
    public ApplicantDTO uploadCV(Long id, MultipartFile file) {
        log.info("Attempting to upload CV for applicant ID: {}", id);
        
        validateFile(file, ALLOWED_CV_TYPES, "CV");
        
        try {
            Applicant applicant = applicantRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Applicant not found with id: " + id));

            // Eliminar CV anterior si existe
            if (applicant.getCvPath() != null) {
                fileStorageService.deleteFile(applicant.getCvPath());
            }
            
            String filePath = fileStorageService.storeFile(file, "cvs");
            applicant.setCvPath(filePath);
            applicant.setLastUpdated(LocalDateTime.now());
            
            Applicant updatedApplicant = applicantRepository.save(applicant);
            log.info("Successfully uploaded CV for applicant ID: {}", id);
            
            return convertToDto(updatedApplicant);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error uploading CV for applicant {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to upload CV", e);
        }
    }

    @Transactional
    public ApplicantDTO uploadProfilePicture(Long id, MultipartFile file) {
        log.info("Attempting to upload profile picture for applicant ID: {}", id);
        
        validateFile(file, ALLOWED_IMAGE_TYPES, "Profile picture");
        
        try {
            Applicant applicant = applicantRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Applicant not found with id: " + id));

            // Eliminar foto anterior si existe
            if (applicant.getProfilePicturePath() != null) {
                fileStorageService.deleteFile(applicant.getProfilePicturePath());
            }
            
            String filePath = fileStorageService.storeFile(file, "profile-pictures");
            applicant.setProfilePicturePath(filePath);
            applicant.setLastUpdated(LocalDateTime.now());
            
            Applicant updatedApplicant = applicantRepository.save(applicant);
            log.info("Successfully uploaded profile picture for applicant ID: {}", id);
            
            return convertToDto(updatedApplicant);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error uploading profile picture for applicant {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to upload profile picture", e);
        }
    }

    @Transactional
    public void deleteApplicant(Long id) {
        log.info("Attempting to delete applicant with ID: {}", id);
        
        try {
            if (!applicantRepository.existsById(id)) {
                throw new ResourceNotFoundException("Applicant not found with id: " + id);
            }
            
            Applicant applicant = applicantRepository.findById(id).get();
            
            // Eliminar archivos asociados
            if (applicant.getCvPath() != null) {
                fileStorageService.deleteFile(applicant.getCvPath());
            }
            if (applicant.getProfilePicturePath() != null) {
                fileStorageService.deleteFile(applicant.getProfilePicturePath());
            }
            
            applicantRepository.deleteById(id);
            log.info("Successfully deleted applicant with ID: {}", id);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error deleting applicant {}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to delete applicant", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicantDTO> getApplicantsByStatus(ApplicantStatus status) {
        log.debug("Fetching applicants with status: {}", status);
        return applicantRepository.findByStatus(status).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApplicantDTO applyToJob(Long jobId, ApplicantRequest request, MultipartFile cv) {
        // Validar archivo CV
        if (cv == null || cv.isEmpty()) {
            throw new IllegalArgumentException("CV file is required");
        }
        
        // Validar tipo de archivo CV
        String contentType = cv.getContentType();
        if (contentType == null || !(contentType.equals("application/pdf") || 
                contentType.equals("application/msword") || 
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))) {
            throw new IllegalArgumentException("CV must be a PDF or Word document");
        }
        
        // Validar tamaño del archivo (máximo 5MB)
        if (cv.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("CV file size must be less than 5MB");
        }
        
        // Obtener usuario actual
        String userEmail = SecurityUtils.getCurrentUserId(); // Esto devuelve el email del usuario
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + userEmail));

        // Obtener trabajo
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found with ID: " + jobId));

        // Validar que el trabajo esté activo
        if (!job.isActive()) {
            throw new IllegalStateException("Cannot apply to an inactive job: " + job.getTitle());
        }

        // Validar que el usuario no haya aplicado ya a este trabajo
        if (applicantRepository.existsByUserIdAndJobId(user.getId(), jobId)) {
            throw new IllegalStateException("You have already applied to this job: " + job.getTitle());
        }

        // Guardar el archivo CV
        String cvPath;
        try {
            cvPath = fileStorageService.storeCV(cv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store CV file: " + e.getMessage(), e);
        }

        // Crear y configurar el aplicante
        Applicant applicant = new Applicant();
        applicant.setUser(user);
        applicant.setJob(job);
        applicant.setCvPath(cvPath);
        
        // Establecer campos obligatorios
        applicant.setExperience(request.getExperience());
        applicant.setEducation(request.getEducation());
        applicant.setSkills(request.getSkills());
        
        // Establecer campos opcionales si están presentes
        if (request.getPhoneNumber() != null) {
            applicant.setPhoneNumber(request.getPhoneNumber());
        }
        
        if (request.getAddress() != null) {
            applicant.setAddress(request.getAddress());
        }
        
        if (request.getDateOfBirth() != null) {
            applicant.setDateOfBirth(request.getDateOfBirth());
        }
        
        // Establecer estado inicial y fechas
        applicant.setStatus(ApplicantStatus.PENDING);
        applicant.setCreatedAt(LocalDateTime.now());
        applicant.setLastUpdated(LocalDateTime.now());
        
        // Analizar el CV con IA si está configurado
        try {
            // Aquí podríamos llamar al servicio de IA para analizar el CV
            // Por ahora, dejamos el aiScore como null
        } catch (Exception e) {
            // Si falla el análisis de IA, continuamos sin establecer el aiScore
            // y registramos el error
            System.err.println("Failed to analyze CV with AI: " + e.getMessage());
        }

        // Guardar el aplicante
        try {
            applicant = applicantRepository.save(applicant);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save applicant: " + e.getMessage(), e);
        }
        
        // Mapear a DTO y agregar información del trabajo
        ApplicantDTO dto = modelMapper.map(applicant, ApplicantDTO.class);
        dto.setJobId(job.getId());
        dto.setJobTitle(job.getTitle());
        
        return dto;
    }

    public List<ApplicantDTO> getApplicantsByJob(Long jobId) {
        String userEmail = SecurityUtils.getCurrentUserId(); // Esto devuelve el email del usuario
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found"));

        // Los administradores pueden ver aplicantes de cualquier trabajo
        if (!job.getRecruiter().getId().equals(user.getId()) && !user.getRole().equals("ADMIN")) {
            throw new AccessDeniedException("You can only view applicants for your own job postings");
        }

        return applicantRepository.findByJobId(jobId).stream()
                .map(applicant -> modelMapper.map(applicant, ApplicantDTO.class))
                .collect(Collectors.toList());
    }

    public List<ApplicantDTO> getMyApplications() {
        String userEmail = SecurityUtils.getCurrentUserId(); // Esto devuelve el email del usuario
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return applicantRepository.findByUserId(user.getId()).stream()
                .map(applicant -> modelMapper.map(applicant, ApplicantDTO.class))
                .collect(Collectors.toList());
    }

    @Transactional
    public ApplicantDTO updateApplicationStatus(Long jobId, Long applicantId, String status) {
        Long userId = Long.parseLong(SecurityUtils.getCurrentUserId());
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("Job not found"));

        if (!job.getRecruiter().getId().equals(userId)) {
            throw new AccessDeniedException("You can only update status for your own job postings");
        }

        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new EntityNotFoundException("Application not found"));

        if (!applicant.getJob().getId().equals(jobId)) {
            throw new IllegalArgumentException("Application does not belong to the specified job");
        }

        applicant.setStatus(ApplicantStatus.valueOf(status.toUpperCase()));
        applicant = applicantRepository.save(applicant);
        return modelMapper.map(applicant, ApplicantDTO.class);
    }

    /**
     * Analiza el CV de un candidato utilizando IA
     * 
     * @param applicantId ID del candidato
     * @return Mapa con los resultados del análisis
     */
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeCV(Long applicantId) {
        log.info("Analyzing CV for applicant with ID: {}", applicantId);
        
        try {
            Applicant applicant = applicantRepository.findById(applicantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Applicant not found with id: " + applicantId));
            
            // Obtener el texto del CV
            String cvPath = applicant.getCvPath();
            if (cvPath == null || cvPath.isEmpty()) {
                throw new InvalidApplicantDataException("Applicant does not have a CV uploaded");
            }
            
            // Obtener el archivo del CV desde el almacenamiento
            // Convertir la ruta relativa a una ruta absoluta usando FileStorageService
            Path cvFilePath = fileStorageService.getFilePath(cvPath);
            java.io.File cvFile = cvFilePath.toFile();
            
            if (!cvFile.exists()) {
                throw new InvalidApplicantDataException("CV file not found at path: " + cvFilePath);
            }
            
            log.info("Accediendo al archivo CV en la ruta: {}", cvFilePath);
            
            // Convertir el archivo a MultipartFile para procesarlo
            MultipartFile multipartFile = convertToMultipartFile(cvFile);
            
            // Extraer el texto del CV utilizando el servicio de extracción de texto
            String cvContent;
            try {
                cvContent = documentTextExtractorService.extractText(multipartFile);
                // Limpiar el texto extraído
                cvContent = documentTextExtractorService.cleanExtractedText(cvContent);
                
                if (cvContent == null || cvContent.isEmpty()) {
                    throw new InvalidApplicantDataException("No se pudo extraer texto del CV");
                }
                
                log.info("Texto extraído del CV con longitud: {}", cvContent.length());
            } catch (IOException e) {
                log.error("Error al extraer texto del CV: {}", e.getMessage(), e);
                throw new InvalidApplicantDataException("Error al procesar el archivo CV: " + e.getMessage());
            }
            
            // Obtener la descripción del trabajo
            Job job = applicant.getJob();
            String jobDescription = job.getDescription();
            
            // Analizar el CV utilizando el servicio de IA
            // Usamos el servicio especializado de Gemini para el análisis de CV en lugar de Hugging Face
            Map<String, Object> analysisResult = cvAnalysisService.analyzeCV(cvContent, jobDescription);
            
            // Calcular puntuación general
            if (!analysisResult.containsKey("error")) {
                double relevance = (double) analysisResult.getOrDefault("relevance", 0.0);
                double technicalSkills = (double) analysisResult.getOrDefault("technicalSkills", 0.0);
                double experience = (double) analysisResult.getOrDefault("experience", 0.0);
                double education = (double) analysisResult.getOrDefault("education", 0.0);
                
                double overallScore = (relevance + technicalSkills + experience + education) / 4.0;
                analysisResult.put("overallScore", Math.round(overallScore * 100.0) / 100.0);
            }
            
            return analysisResult;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error analyzing CV for applicant {}: {}", applicantId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Failed to analyze CV: " + e.getMessage());
            return errorResult;
        }
    }

    private void validateFile(MultipartFile file, List<String> allowedTypes, String fileType) {
        if (file.isEmpty()) {
            throw new InvalidFileException(fileType + " file is empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileException(fileType + " file size exceeds maximum limit of 10MB");
        }
        
        if (!allowedTypes.contains(file.getContentType())) {
            throw new InvalidFileException("Invalid " + fileType + " file type. Allowed types: " + String.join(", ", allowedTypes));
        }
    }

    private void validateApplicantData(ApplicantDTO applicantDto) {
        List<String> errors = new ArrayList<>();
        
        if (applicantDto.getPhoneNumber() != null && !isValidPhoneNumber(applicantDto.getPhoneNumber())) {
            errors.add("Invalid phone number format. Must be in format: +51 999999999 or 999999999");
        }
        
        if (applicantDto.getDateOfBirth() != null && applicantDto.getDateOfBirth().isAfter(LocalDateTime.now().toLocalDate())) {
            errors.add("Date of birth cannot be in the future");
        }
        
        if (!errors.isEmpty()) {
            throw new InvalidApplicantDataException("Invalid applicant data: " + String.join(", ", errors));
        }
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        // Formato: +51 999999999 o 999999999
        return phoneNumber.matches("^(\\+51 )?\\d{9}$");
    }

    private ApplicantDTO convertToDto(Applicant applicant) {
        return modelMapper.map(applicant, ApplicantDTO.class);
    }

    private Applicant convertToEntity(ApplicantDTO applicantDto) {
        return modelMapper.map(applicantDto, Applicant.class);
    }
    
    /**
     * Convierte un archivo File a MultipartFile para procesarlo
     * 
     * @param file Archivo a convertir
     * @return MultipartFile creado a partir del archivo
     * @throws IOException Si ocurre un error al leer el archivo
     */
    private MultipartFile convertToMultipartFile(File file) throws IOException {
        final String name = file.getName();
        final String originalFilename = file.getName();
        final String contentType = determineContentType(file.getName());
        final boolean isEmpty = file.length() == 0;
        
        return new MultipartFile() {
            @Override
            @NonNull
            public String getName() {
                return name;
            }
            
            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }
            
            @Override
            public String getContentType() {
                return contentType;
            }
            
            @Override
            public boolean isEmpty() {
                return isEmpty;
            }
            
            @Override
            public long getSize() {
                return file.length();
            }
            
            @Override
            @NonNull
            public byte[] getBytes() throws IOException {
                try (InputStream is = new FileInputStream(file)) {
                    return is.readAllBytes();
                }
            }
            
            @Override
            @NonNull
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(file);
            }
            
            @Override
            public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
                try (FileInputStream fis = new FileInputStream(file);
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, n);
                    }
                }
            }
        };
    }
    
    /**
     * Determina el tipo de contenido basado en la extensión del archivo
     */
    private String determineContentType(String filename) {
        if (filename == null) {
            return null;
        }
        filename = filename.toLowerCase();
        if (filename.endsWith(".pdf")) {
            return "application/pdf";
        } else if (filename.endsWith(".doc")) {
            return "application/msword";
        } else if (filename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            return "application/octet-stream";
        }
    }
}
