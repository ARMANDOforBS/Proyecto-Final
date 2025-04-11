package com.utp.proyectoFinal.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utp.proyectoFinal.dto.auth.AuthResponse;
import com.utp.proyectoFinal.dto.auth.LoginRequest;
import com.utp.proyectoFinal.dto.auth.RegisterRequest;
import com.utp.proyectoFinal.dto.UserDTO;
import com.utp.proyectoFinal.exception.UserAlreadyExistsException;
import com.utp.proyectoFinal.model.Role;
import com.utp.proyectoFinal.model.User;
import com.utp.proyectoFinal.repository.UserRepository;
import com.utp.proyectoFinal.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email already in use: " + request.getEmail());
        }
        
        // Create new user
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : Role.APPLICANT)
                .build();
        
        User savedUser = userRepository.save(user);
        
        // Generate JWT token
        String token = jwtService.generateToken(savedUser);
        
        UserDTO userDTO = new UserDTO();
        userDTO.setId(savedUser.getId());
        userDTO.setEmail(savedUser.getEmail());
        userDTO.setFirstName(savedUser.getFirstName());
        userDTO.setLastName(savedUser.getLastName());
        userDTO.setRole(savedUser.getRole());
        
        return AuthResponse.builder()
                .accessToken(token)
                .user(userDTO)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        // Set authentication in security context
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // Get authenticated user
        User user = (User) authentication.getPrincipal();
        
        // Generate JWT token
        String token = jwtService.generateToken(user);
        
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setEmail(user.getEmail());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setRole(user.getRole());
        
        return AuthResponse.builder()
                .accessToken(token)
                .user(userDTO)
                .build();
    }
    
    public AuthResponse refreshToken(User user) {
        String token = jwtService.generateToken(user);
        
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setEmail(user.getEmail());
        userDTO.setFirstName(user.getFirstName());
        userDTO.setLastName(user.getLastName());
        userDTO.setRole(user.getRole());
        
        return AuthResponse.builder()
                .accessToken(token)
                .user(userDTO)
                .build();
    }
}
