package com.concerto.omnichannel.controller;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.dto.ApiResponse;
import com.concerto.omnichannel.model.ErrorDetails;
import com.concerto.omnichannel.service.AsyncExternalSwitchConnector;
import com.concerto.omnichannel.service.ISO8583MessageParser;
import com.concerto.omnichannel.service.MainOrchestrator;
import com.concerto.omnichannel.service.TransactionService;
import com.concerto.omnichannel.utils.ValidationUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transaction Processing", description = "Omni-channel transaction processing endpoints")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    @Autowired
    private MainOrchestrator mainOrchestrator;

    @Autowired
    private TransactionService transactionService;

    @PostMapping("/process")
    @Operation(
            summary = "Process transaction",
            description = "Process transaction from various channels (POS, ATM, UPI, BBPS, PG)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Transaction processed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication failed",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "Business validation failed",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            )
    })
    public ResponseEntity<ApiResponse<TransactionResponse>> processTransaction(
            @Valid @RequestBody TransactionRequest request,
            @Parameter(description = "Client ID for authentication")
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @Parameter(description = "Client Secret for authentication")
            @RequestHeader(value = "X-Client-Secret", required = false) String clientSecret,
            @Parameter(description = "JWT Token for authentication")
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Parameter(description = "Request correlation ID for tracking")
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            HttpServletRequest httpRequest) {

        logger.info("Received request - ClientId: {}, CorrelationId: {}", clientId, correlationId);
        logger.info("Request body: {}", request);

        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        logger.debug("correlationId in request {}", correlationId);

        // Set MDC for logging
        MDC.put("correlationId", correlationId);
        MDC.put("channel", request.getChannel());
        MDC.put("operation", request.getOperation());
        MDC.put("clientIp", getClientIpAddress(httpRequest));

        logger.info("Processing transaction request for channel: {} operation: {}",
                request.getChannel(), request.getOperation());

        try {
            // Extract JWT token from Authorization header
            String jwtToken = extractJwtToken(authorizationHeader);

            // Validate request
            ValidationUtils.validateTransactionRequest(request);

            // Process transaction
            TransactionResponse response = mainOrchestrator.orchestrate(
                    request, clientId, clientSecret, jwtToken, correlationId
            );

            // Determine HTTP status based on transaction success
            HttpStatus status = response.isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;

            ApiResponse<TransactionResponse> apiResponse = ApiResponse.<TransactionResponse>builder()
                    .success(response.isSuccess())
                    .data(response)
                    .message(response.isSuccess() ? "Transaction processed successfully" : "Transaction processing failed")
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();

            logger.info("Transaction processing completed with status: {}", response.isSuccess() ? "SUCCESS" : "FAILED");
            return ResponseEntity.status(status).body(apiResponse);

        } catch (SecurityException e) {
            logger.error("Authentication failed", e);
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed", correlationId, e);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request", e);
            return createErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request: " + e.getMessage(), correlationId, e);

        } catch (RuntimeException e) {
            logger.error("Transaction processing failed", e);
            return createErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Transaction processing failed", correlationId, e);

        } catch (Exception e) {
            logger.error("Unexpected error during transaction processing", e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", correlationId, e);

        } finally {
            MDC.clear();
        }
    }

    /*@PostMapping("/process-async")
    @Operation(
            summary = "Process transaction asynchronously",
            description = "Process transaction asynchronously for high-volume channels",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<String>> processTransactionAsync(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-Client-Secret", required = false) String clientSecret,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            HttpServletRequest httpRequest) {

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);
        MDC.put("channel", request.getChannel());

        try {
            String jwtToken = extractJwtToken(authorizationHeader);
            ValidationUtils.validateTransactionRequest(request);

            // Start async processing
            CompletableFuture<TransactionResponse> futureResponse = mainOrchestrator.orchestrateAsync(
                    request, clientId, clientSecret, jwtToken
            );

            // Return immediately with correlation ID
            ApiResponse<String> response = ApiResponse.<String>builder()
                    .success(true)
                    .data(correlationId)
                    .message("Transaction submitted for processing")
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();

            logger.info("Transaction submitted for async processing");
            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            logger.error("Failed to submit transaction for async processing", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(createErrorApiResponse("Failed to submit transaction", correlationId, e));
        } finally {
            MDC.clear();
        }
    }*/


    @PostMapping("/process-async")
    @Operation(
            summary = "Process transaction asynchronously",
            description = "Process transaction asynchronously and return actual ISO response",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public CompletableFuture<ResponseEntity<ApiResponse<TransactionResponse>>> processTransactionAsync(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-Client-Secret", required = false) String clientSecret,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            HttpServletRequest httpRequest) {

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;
        MDC.put("correlationId", correlationId);
        MDC.put("channel", request.getChannel());

        try {
            String jwtToken = extractJwtToken(authorizationHeader);
            ValidationUtils.validateTransactionRequest(request);

            logger.info("Starting async transaction processing for correlation: {}", correlationId);

            // Start async processing and return the CompletableFuture
            return mainOrchestrator.orchestrateAsync(request, clientId, clientSecret, jwtToken, correlationId)
                    .thenApply(transactionResponse -> {
                        // Success - return actual ISO response
                        logger.info("Async transaction completed successfully for correlation: {}", finalCorrelationId);

                        ApiResponse<TransactionResponse> response = ApiResponse.<TransactionResponse>builder()
                                .success(true)
                                .data(transactionResponse)
                                .message("Transaction processed successfully")
                                .timestamp(LocalDateTime.now())
                                .correlationId(finalCorrelationId)
                                .build();

                        return ResponseEntity.ok(response);
                    })
                    .exceptionally(throwable -> {
                        // Error handling
                        logger.error("Async transaction failed for correlation: {}", finalCorrelationId, throwable);

                        ApiResponse<TransactionResponse> errorResponse = ApiResponse.<TransactionResponse>builder()
                                .success(false)
                                .data(null)
                                .message("Transaction failed: " + throwable.getMessage())
                                .timestamp(LocalDateTime.now())
                                .correlationId(finalCorrelationId)
                                .error(throwable.getMessage() != null ? throwable.getMessage() : "Unknown error occurred")
                                .build();

                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
                    })
                    .orTimeout(30, TimeUnit.SECONDS) // Add timeout
                    .handle((result, timeoutException) -> {
                        if (timeoutException instanceof TimeoutException) {
                            logger.error("Transaction timed out for correlation: {}", finalCorrelationId);

                            ApiResponse<TransactionResponse> timeoutResponse = ApiResponse.<TransactionResponse>builder()
                                    .success(false)
                                    .data(null)
                                    .message("Transaction timed out")
                                    .timestamp(LocalDateTime.now())
                                    .correlationId(finalCorrelationId)
                                    .build();

                            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse);
                        }
                        return result;
                    });

        } catch (Exception e) {
            logger.error("Failed to initiate async transaction processing", e);

            // Return failed CompletableFuture for immediate errors
            ApiResponse<TransactionResponse> errorResponse = ApiResponse.<TransactionResponse>builder()
                    .success(false)
                    .data(null)
                    .message("Failed to initiate transaction")
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .error(e.getMessage() != null ? e.getMessage() : "Unknown error occurred")
                    .build();

            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse));
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/status/{transactionId}")
    @Operation(
            summary = "Get transaction status",
            description = "Retrieve the current status of a transaction"
    )
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransactionStatus(
            @PathVariable Long transactionId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlationId", correlationId);

        try {
            TransactionResponse response = transactionService.getTransactionStatus(transactionId);

            ApiResponse<TransactionResponse> apiResponse = ApiResponse.<TransactionResponse>builder()
                    .success(true)
                    .data(response)
                    .message("Transaction status retrieved successfully")
                    .timestamp(LocalDateTime.now())
                    .correlationId(correlationId)
                    .build();

            return ResponseEntity.ok(apiResponse);

        } catch (RuntimeException e) {
            logger.error("Failed to retrieve transaction status", e);
            return createErrorResponse(HttpStatus.NOT_FOUND, "Transaction not found", correlationId, e);
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        ApiResponse<String> response = ApiResponse.<String>builder()
                .success(true)
                .data("OK")
                .message("Transaction service is healthy")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    // Helper methods
    private String extractJwtToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        return null;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private ResponseEntity<ApiResponse<TransactionResponse>> createErrorResponse(
            HttpStatus status, String message, String correlationId, Exception e) {

        ApiResponse<TransactionResponse> response = ApiResponse.<TransactionResponse>builder()
                .success(false)
                .data(null)
                .message(message)
                .error(e.getMessage())
                .timestamp(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(status).body(response);
    }

    private ApiResponse<String> createErrorApiResponse(String message, String correlationId, Exception e) {
        return ApiResponse.<String>builder()
                .success(false)
                .data(null)
                .message(message)
                .error(e.getMessage())
                .timestamp(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
    }

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ISO8583MessageParser messageParser;

    @Autowired
    private AsyncExternalSwitchConnector asyncSwitchConnector;
    @PostMapping("/test-performance")
    public CompletableFuture<ResponseEntity<String>> testPerformance(@RequestBody TransactionRequest request) throws Exception {
        return asyncSwitchConnector.sendToSwitchWithPool(
                        messageParser.jsonToISO8583FromJson(objectMapper.writeValueAsString(request)),
                        request.getChannel())
                .thenApply(isoResponse -> {
                    try {
                        Map<String, Object> response = messageParser.iso8583ToJson(isoResponse);
                        return ResponseEntity.ok(objectMapper.writeValueAsString(response));
                    } catch (Exception e) {
                        return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
                    }
                })
                .orTimeout(10, TimeUnit.SECONDS);
    }
}