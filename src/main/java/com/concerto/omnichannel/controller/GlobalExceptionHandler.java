package com.concerto.omnichannel.controller;

import com.concerto.omnichannel.dto.ApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String correlationId = MDC.get("correlationId");

        BindingResult result = ex.getBindingResult();
        List<String> errors = new ArrayList<>();

        for (FieldError error : result.getFieldErrors()) {
            errors.add(error.getField() + ": " + error.getDefaultMessage());
        }

        logger.warn("Validation failed: {}", errors);

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Validation failed")
                .error(String.join(", ", errors))
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        String correlationId = MDC.get("correlationId");

        logger.warn("Invalid argument: {}", ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Invalid request")
                .error(ex.getMessage())
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Object>> handleSecurityException(SecurityException ex) {
        String correlationId = MDC.get("correlationId");
        String clientIp = MDC.get("clientIp");

        logger.error("Security exception from IP {}: {}", clientIp, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Authentication failed")
                .error("Access denied")
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ApiResponse<Object>> handleCircuitBreakerException(CallNotPermittedException ex) {
        String correlationId = MDC.get("correlationId");
        String channel = MDC.get("channel");

        logger.error("Circuit breaker open for channel {}: {}", channel, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Service temporarily unavailable")
                .error("Circuit breaker is open for channel: " + channel)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler({TimeoutException.class})
    public ResponseEntity<ApiResponse<Object>> handleTimeoutException(Exception ex) {
        String correlationId = MDC.get("correlationId");
        String channel = MDC.get("channel");

        logger.error("Timeout occurred for channel {}: {}", channel, ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Request timeout")
                .error("Operation timed out for channel: " + channel)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String correlationId = MDC.get("correlationId");

        logger.warn("Invalid JSON request: {}", ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Invalid JSON format")
                .error("Request body contains invalid JSON")
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        String correlationId = MDC.get("correlationId");

        logger.warn("Missing required header: {}", ex.getHeaderName());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Missing required header")
                .error("Required header '" + ex.getHeaderName() + "' is missing")
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String correlationId = MDC.get("correlationId");

        logger.warn("Type mismatch for parameter {}: {}", ex.getName(), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Invalid parameter type")
                .error("Parameter '" + ex.getName() + "' has invalid type")
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException ex) {
        String correlationId = MDC.get("correlationId");
        String channel = MDC.get("channel");

        logger.error("Runtime exception in channel {}: ", channel, ex);

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Transaction processing failed")
                .error("An error occurred while processing the transaction")
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        String correlationId = MDC.get("correlationId");

        logger.error("Unexpected error: ", ex);

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .message("Internal server error")
                .error("An unexpected error occurred")
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}


