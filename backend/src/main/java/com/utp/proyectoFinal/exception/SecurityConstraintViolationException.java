package com.utp.proyectoFinal.exception;

public class SecurityConstraintViolationException extends RuntimeException {
    public SecurityConstraintViolationException(String message) {
        super(message);
    }
} 