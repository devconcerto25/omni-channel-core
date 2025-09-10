package com.concerto.omnichannel.exceptions;

public class CorrelationException extends RuntimeException {
    public CorrelationException(String message, Throwable cause) {
        super(message, cause);
    }
}
