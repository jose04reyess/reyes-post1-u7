package com.example.multas.domain;

public class PagoRechazadoException extends RuntimeException {

    public PagoRechazadoException(String message) {
        super(message);
    }

    public PagoRechazadoException(String message, Throwable cause) {
        super(message, cause);
    }
}
