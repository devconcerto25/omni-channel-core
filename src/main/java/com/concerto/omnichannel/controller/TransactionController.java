package com.concerto.omnichannel.controller;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.dto.ApiResponse;
import com.concerto.omnichannel.service.MainOrchestrator;
import com.concerto.omnichannel.service.TransactionService;
import com.concerto.omnichannel.utils.ValidationUtils;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


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
                    request, clientId, clientSecret, jwtToken
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

    @PostMapping("/process-async")
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
}