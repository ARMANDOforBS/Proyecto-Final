package com.utp.proyectoFinal.service;

import java.time.LocalDateTime;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utp.proyectoFinal.dto.UserDTO;
import com.utp.proyectoFinal.dto.auth.AuthResponse;
import com.utp.proyectoFinal.dto.auth.LoginRequest;
import com.utp.proyectoFinal.dto.auth.RegisterRequest;
import com.utp.proyectoFinal.exception.AccountDeactivatedException;
import com.utp.proyectoFinal.exception.InvalidCredentialsException;
import com.utp.proyectoFinal.exception.InvalidPasswordException;
import com.utp.proyectoFinal.exception.RegistrationFailedException;
import com.utp.proyectoFinal.exception.UserAlreadyExistsException;
import com.utp.proyectoFinal.model.Role;
import com.utp.proyectoFinal.model.User;
import com.utp.proyectoFinal.repository.UserRepository;
import com.utp.proyectoFinal.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Attempting to register new user with email: {}", request.getEmail());
        
        // Validación de contraseña
        if (!isPasswordValid(request.getPassword())) {
            throw new InvalidPasswordException("Password must be at least 8 characters long and contain at least one number and one special character");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email already in use - {}", request.getEmail());
            throw new UserAlreadyExistsException("Email already in use");
        }
        
        try {
            Role role = request.getRole() != null ? request.getRole() : Role.APPLICANT;
            
            User user = User.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(role)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            User savedUser = userRepository.save(user);
            
            String accessToken = jwtService.generateToken(savedUser);
            String refreshToken = jwtService.generateRefreshToken(savedUser);
            
            log.info("Successfully registered new user with ID: {}", savedUser.getId());
            
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(mapToUserDTO(savedUser))
                    .build();
        } catch (Exception e) {
            log.error("Error during user registration: {}", e.getMessage(), e);
            throw new RegistrationFailedException("Failed to register user", e);
        }
    }
    
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getEmail());
        
        try {
            // Primero verificamos si el usuario existe y está activo
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
            
            if (!user.isActive()) {
                log.warn("Login attempt for inactive account: {}", request.getEmail());
                throw new AccountDeactivatedException("Account is deactivated. Please contact support.");
            }
            
            // Luego intentamos la autenticación
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            
            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);
            
            // Actualizar último login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
            
            log.info("Successful login for user: {}", user.getId());
            
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .user(mapToUserDTO(user))
                    .build();
        } catch (AuthenticationException e) {
            log.warn("Failed login attempt for user: {}", request.getEmail());
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }

    private UserDTO mapToUserDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    private boolean isPasswordValid(String password) {
        // Mínimo 8 caracteres, al menos un número y un carácter especial
        String pattern = "^(?=.*[0-9])(?=.*[!@#$%^&*])(?=\\S+$).{8,}$";
        return password.matches(pattern);
    }
}
