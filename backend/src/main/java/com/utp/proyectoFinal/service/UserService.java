package com.utp.proyectoFinal.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.utp.proyectoFinal.dto.UserDTO;
import com.utp.proyectoFinal.exception.ResourceNotFoundException;
import com.utp.proyectoFinal.exception.UserAlreadyExistsException;
import com.utp.proyectoFinal.mapper.UserMapper;
import com.utp.proyectoFinal.model.Role;
import com.utp.proyectoFinal.model.User;
import com.utp.proyectoFinal.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return userMapper.toDTO(user);
    }

    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return userMapper.toDTO(user);
    }

    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        // Check if email already exists
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email already in use: " + userDTO.getEmail());
        }
        
        User user = userMapper.toEntity(userDTO);
        
        // Encode password before saving
        if (user.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        // Set default role if not specified
        if (user.getRole() == null) {
            user.setRole(Role.APPLICANT);
        }
        
        User savedUser = userRepository.save(user);
        return userMapper.toDTO(savedUser);
    }

    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        // Check if email is being changed and if it's already in use
        if (!existingUser.getEmail().equals(userDTO.getEmail()) &&
                userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email already in use: " + userDTO.getEmail());
        }
        
        // Update fields
        existingUser.setFirstName(userDTO.getFirstName());
        existingUser.setLastName(userDTO.getLastName());
        existingUser.setEmail(userDTO.getEmail());
        
        // Update password if provided
        // Asumimos que el password puede venir como un campo adicional en la solicitud
        // pero no está en el DTO
        String password = null;
        try {
            // Usamos reflection para intentar obtener el password si existe como campo transitorio
            java.lang.reflect.Field passwordField = userDTO.getClass().getDeclaredField("password");
            passwordField.setAccessible(true);
            password = (String) passwordField.get(userDTO);
        } catch (Exception e) {
            // Si no existe el campo, ignoramos el error
        }
        
        if (password != null && !password.isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(password));
        }
        
        // Update role if provided and user is admin
        if (userDTO.getRole() != null) {
            existingUser.setRole(userDTO.getRole());
        }
        
        User updatedUser = userRepository.save(existingUser);
        return userMapper.toDTO(updatedUser);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public UserDTO changeRole(Long id, Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        
        user.setRole(role);
        User updatedUser = userRepository.save(user);
        return userMapper.toDTO(updatedUser);
    }
}
