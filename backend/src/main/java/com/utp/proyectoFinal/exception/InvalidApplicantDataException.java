package com.utp.proyectoFinal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidApplicantDataException extends RuntimeException {
    public InvalidApplicantDataException(String message) {
        super(message);
    }
}
