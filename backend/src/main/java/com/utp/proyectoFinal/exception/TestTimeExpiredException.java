package com.utp.proyectoFinal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TestTimeExpiredException extends RuntimeException {
    public TestTimeExpiredException(String message) {
        super(message);
    }
}
