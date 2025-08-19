// Updated MainOrchestrator using Connector Factory Pattern
package com.concerto.omnichannel.service;

import com.concerto.omnichannel.connector.Connector;
import com.concerto.omnichannel.connector.ConnectorFactory;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.entity.TransactionHeader;
import com.concerto.omnichannel.repository.TransactionHeaderRepository;
import com.concerto.omnichannel.validation.BusinessRuleValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class MainOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(MainOrchestrator.class);

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private ConnectorFactory connectorFactory;

    @Autowired
    private TransactionHeaderRepository transactionHeaderRepository;

    @Autowired
    private BusinessRuleValidator businessRuleValidator;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @Autowired
    private TimeLimiterRegistry timeLimiterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional
    public CompletableFuture<TransactionResponse> orchestrateAsync(
            TransactionRequest request,
            String clientId,
            String clientSecret,
            String token) {

        // Generate correlation ID for tracing
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("channel", request.getChannel());
        MDC.put("operation", request.getOperation());

        logger.info("Starting async transaction orchestration for channel: {} operation: {}",
                request.getChannel(), request.getOperation());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Set MDC in async context
                MDC.put("correlationId", correlationId);
                MDC.put("channel", request.getChannel());

                return orchestrateInternal(request, clientId, clientSecret, token, correlationId);

            } catch (Exception e) {
                logger.error("Async orchestration failed with error", e);
                throw new CompletionException(e);
            } finally {
                MDC.clear();
            }
        });
    }

    public TransactionResponse orchestrate(TransactionRequest request,
                                           String clientId,
                                           String clientSecret,
                                           String token) {
        String correlationId = UUID.randomUUID().toString();
        return orchestrateInternal(request, clientId, clientSecret, token, correlationId);
    }

    private TransactionResponse orchestrateInternal(TransactionRequest request,
                                                    String clientId,
                                                    String clientSecret,
                                                    String token,
                                                    String correlationId) {

        // 1. Create transaction header for tracking
        TransactionHeader header = createTransactionHeader(request, correlationId);

        try {
            // 2. Business rule validation
            businessRuleValidator.validateBusinessRules(request);

            // 3. Authentication with circuit breaker
            boolean authenticated = executeWithResilience(
                    "authentication",
                    () -> authenticationService.authenticate(clientId, clientSecret, token, request.getChannel()),
                    Duration.ofSeconds(2)
            );

            if (!authenticated) {
                header.setStatus("AUTH_FAILED");
                header.setErrorMessage("Authentication failed");
                header.setResponseTimestamp(LocalDateTime.now());
                transactionHeaderRepository.save(header);

                throw new SecurityException("Authentication failed for channel: " + request.getChannel());
            }

            header.setStatus("AUTHENTICATED");
            transactionHeaderRepository.save(header);

            // 4. Get appropriate connector using factory pattern
            Connector connector = connectorFactory.getConnector(request.getChannel());

            logger.info("Using connector: {} for channel: {}",
                    connector.getConnectorType(), request.getChannel());

            // 5. Update processing status
            header.setStatus("PROCESSING");
            transactionHeaderRepository.save(header);

            // 6. Process transaction through connector with resilience patterns
            String requestPayload = objectMapper.writeValueAsString(request);
            String responsePayload = executeWithResilience(
                    request.getChannel() + "-" + request.getOperation(),
                    () -> {
                        try {
                            return connector.process(requestPayload);
                        } catch (Exception e) {
                            throw new RuntimeException("Connector processing failed", e);
                        }
                    },
                    getTimeoutForChannel(request.getChannel())
            );

            // 7. Parse response and create transaction response
            TransactionResponse response = parseConnectorResponse(responsePayload, request, header);

            // 8. Update final status
            header.setStatus(response.isSuccess() ? "SUCCESS" : "FAILED");
            if (!response.isSuccess()) {
                header.setErrorMessage(response.getErrorMessage());
                header.setErrorCode(response.getErrorCode());
            }
            header.setResponseTimestamp(LocalDateTime.now());
            transactionHeaderRepository.save(header);

            response.setCorrelationId(correlationId);
            response.setTransactionId(header.getId());

            logger.info("Transaction orchestration completed with status: {}",
                    response.isSuccess() ? "SUCCESS" : "FAILED");
            return response;

        } catch (Exception e) {
            // Update transaction status on failure
            header.setStatus("FAILED");
            header.setErrorMessage(e.getMessage());
            header.setResponseTimestamp(LocalDateTime.now());
            transactionHeaderRepository.save(header);

            logger.error("Transaction orchestration failed", e);

            // Create error response
            return createErrorResponse(request, header, correlationId, e);
        }
    }

    private TransactionHeader createTransactionHeader(TransactionRequest request, String correlationId) {
        TransactionHeader header = new TransactionHeader();
        header.setChannel(request.getChannel());
        header.setOperation(request.getOperation());
        header.setCorrelationId(correlationId);
        header.setRequestTimestamp(LocalDateTime.now());
        header.setStatus("RECEIVED");

        if (request.getPayload() != null) {
            header.setTransactionType(request.getPayload().getTransactionType());
            header.setAmount(request.getPayload().getAmount());
            header.setCurrency(request.getPayload().getCurrency());
            header.setMerchantId(request.getPayload().getMerchantId());
            header.setTerminalId(request.getPayload().getTerminalId());
        }

        return transactionHeaderRepository.save(header);
    }

    private TransactionResponse parseConnectorResponse(String responsePayload,
                                                       TransactionRequest request,
                                                       TransactionHeader header) {
        try {
            // Parse the JSON response from connector
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

            TransactionResponse response = new TransactionResponse();
            response.setChannel(request.getChannel());
            response.setOperation(request.getOperation());
            response.setTransactionId(header.getId());
            response.setCorrelationId(header.getCorrelationId());

            // Check success status from connector response
            Boolean success = (Boolean) responseMap.get("success");
            if (success == null) {
                // For backward compatibility, check other success indicators
                success = checkSuccessIndicators(responseMap);
            }

            response.setSuccess(success);
            response.setPayload(responsePayload);

            // Extract common fields
            if (responseMap.containsKey("authorizationCode")) {
                response.addAdditionalData("authorizationCode", responseMap.get("authorizationCode"));
            }

            if (responseMap.containsKey("rrn")) {
                response.setExternalReference((String) responseMap.get("rrn"));
            } else if (responseMap.containsKey("transactionId")) {
                response.setExternalReference((String) responseMap.get("transactionId"));
            }

            if (!success) {
                response.setErrorCode((String) responseMap.get("errorCode"));
                response.setErrorMessage((String) responseMap.get("errorMessage"));
            }

            return response;

        } catch (Exception e) {
            logger.error("Failed to parse connector response", e);

            // Create error response if parsing fails
            TransactionResponse errorResponse = new TransactionResponse();
            errorResponse.setChannel(request.getChannel());
            errorResponse.setOperation(request.getOperation());
            errorResponse.setTransactionId(header.getId());
            errorResponse.setCorrelationId(header.getCorrelationId());
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("Failed to parse connector response");
            errorResponse.setErrorCode("RESPONSE_PARSE_ERROR");
            errorResponse.setPayload(responsePayload);

            return errorResponse;
        }
    }

    private boolean checkSuccessIndicators(Map<String, Object> responseMap) {
        // Check various success indicators based on connector type

        // ISO8583 responses
        if (responseMap.containsKey("responseCode")) {
            return "00".equals(responseMap.get("responseCode"));
        }

        // UPI responses
        if (responseMap.containsKey("status")) {
            String status = (String) responseMap.get("status");
            return "SUCCESS".equalsIgnoreCase(status) || "APPROVED".equalsIgnoreCase(status);
        }

        // BBPS responses
        if (responseMap.containsKey("billStatus")) {
            return "PAID".equalsIgnoreCase((String) responseMap.get("billStatus"));
        }

        // Default to false if no success indicator found
        return false;
    }

    private TransactionResponse createErrorResponse(TransactionRequest request,
                                                    TransactionHeader header,
                                                    String correlationId,
                                                    Exception e) {
        TransactionResponse errorResponse = new TransactionResponse();
        errorResponse.setCorrelationId(correlationId);
        errorResponse.setTransactionId(header.getId());
        errorResponse.setChannel(request.getChannel());
        errorResponse.setOperation(request.getOperation());
        errorResponse.setSuccess(false);
        errorResponse.setErrorMessage(e.getMessage());

        // Set appropriate error code based on exception type
        if (e instanceof SecurityException) {
            errorResponse.setErrorCode("AUTH_FAILED");
        } else if (e instanceof IllegalArgumentException) {
            errorResponse.setErrorCode("INVALID_REQUEST");
        } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
            errorResponse.setErrorCode("TIMEOUT");
        } else {
            errorResponse.setErrorCode("PROCESSING_ERROR");
        }

        return errorResponse;
    }

    private <T> T executeWithResilience(String name, Supplier<T> supplier, Duration timeout) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        Retry retry = retryRegistry.retry(name);

        // Decorate the supplier with CircuitBreaker and Retry
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, supplier));

        try {
            // Execute with manual timeout using CompletableFuture
            CompletableFuture<T> future = CompletableFuture.supplyAsync(decoratedSupplier);
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.error("Operation timed out for: {}", name);
            throw new RuntimeException("Operation timed out: " + name, e);
        } catch (Exception e) {
            logger.error("Resilience pattern execution failed for: {}", name, e);
            throw new RuntimeException("Service temporarily unavailable: " + name, e);
        }
    }

    private Duration getTimeoutForChannel(String channel) {
        // This could be moved to configuration service
        switch (channel.toUpperCase()) {
            case "BBPS":
                return Duration.ofSeconds(3);
            case "ISO8583":
            case "POS":
            case "ATM":
                return Duration.ofSeconds(5);
            case "UPI":
                return Duration.ofSeconds(6);
            case "PG":
                return Duration.ofSeconds(8);
            default:
                return Duration.ofSeconds(10);
        }
    }
}