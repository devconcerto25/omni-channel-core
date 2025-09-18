package com.concerto.omnichannel.service;

import com.concerto.omnichannel.connector.Connector;
import com.concerto.omnichannel.connector.ConnectorFactory;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.entity.TransactionHeader;
import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.registry.OperationHandlerRegistry;
import com.concerto.omnichannel.registry.OperationRegistry;
import com.concerto.omnichannel.repository.TransactionHeaderRepository;
import com.concerto.omnichannel.validation.BusinessRuleValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
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

    @Autowired
    private ChannelSpecificTransactionService channelSpecificTransactionService;

    @Autowired
    private STANGenerationService stanGenerationService;

    private static final String STAN_FORMAT = "%06d";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private OperationRegistry handlerRegistry;

    public TransactionResponse orchestrate(TransactionRequest request,
                                           String clientId,
                                           String clientSecret,
                                           String token, String correlationId) {
        //String correlationId = UUID.randomUUID().toString();
        //logger.debug("new correlationId");
        return orchestrateInternal(request, clientId, clientSecret, token, correlationId);
    }

    // SYNCHRONOUS VERSION - Optimized for high throughput
    private TransactionResponse orchestrateInternal(TransactionRequest request,
                                                    String clientId,
                                                    String clientSecret,
                                                    String token,
                                                    String correlationId) {
        try {
            // 1. Store request in Redis immediately (fast operation)
            storeRequestInRedis(correlationId, request, clientId, clientSecret, token);

            // 2. Skipping business validation for performance (can be added back later)
            businessRuleValidator.validateBusinessRules(request);

            // 3. Skipping authentication for performance testing (can be added back later)
            boolean authenticated = executeWithResilience(
                    "authentication",
                    () -> authenticationService.authenticate(clientId, clientSecret, token, request.getChannel(), correlationId),
                    Duration.ofSeconds(2)
            );

            if (!authenticated) {
                updateRedisWithAuthFailure(correlationId, "Authentication Failed");
                throw new SecurityException("Authentication failed for channel: " + request.getChannel());
            }

            // 4. Process through connector (only essential operation)
            /*Connector connector = connectorFactory.getConnector(request.getChannel());
            logger.info("Using connector: {} for channel: {}", connector.getConnectorType(), request.getChannel());

            String responsePayload = connector.process(request);

            // 5. Parse response (fast, CPU-bound)
            TransactionResponse response = parseConnectorResponseSimple(responsePayload, request, correlationId);*/

            OperationHandler handler = handlerRegistry.getHandler(request.getChannel(), request.getOperation());
            if (handler == null) {
                return createErrorResponse(request, null, correlationId, new Exception("Unsupported channel/operation: " + request.getChannel() + "/" + request.getOperation()));
            }

            TransactionResponse response = handler.handle(request);

            // 6. Store response in Redis (fast operation)
            storeResponseInRedis(correlationId, response);

            // 7. Trigger async persistence (fire and forget)
            persistDataAsync(correlationId);

            logger.info("Transaction orchestration completed with status: {}",
                    response.isSuccess() ? "SUCCESS" : "FAILED");
            return response;

        } catch (Exception e) {
            logger.error("Transaction orchestration failed", e);

            // Store error in Redis and create error response
            TransactionResponse errorResponse = createErrorResponse(request, null, correlationId, e);
            storeResponseInRedis(correlationId, errorResponse);
            persistDataAsync(correlationId);

            return errorResponse;
        }
    }

    // ASYNCHRONOUS VERSION - Fully non-blocking
    public CompletableFuture<TransactionResponse> orchestrateAsync(
            TransactionRequest request,
            String clientId,
            String clientSecret,
            String token,
            String correlationId) {

        logger.info("Starting async transaction orchestration for channel: {} operation: {}",
                request.getChannel(), request.getOperation());

        return CompletableFuture
                .supplyAsync(() -> {
                    // Store request in Redis
                    storeRequestInRedis(correlationId, request, clientId, clientSecret, token);
                    return request;
                })
                .thenCompose(req -> {
                    businessRuleValidator.validateBusinessRules(request);

                    boolean authenticated = executeWithResilience(
                            "authentication",
                            () -> authenticationService.authenticate(clientId, clientSecret, token, request.getChannel(), correlationId),
                            Duration.ofSeconds(2)
                    );

                    if (!authenticated) {
                        updateRedisWithAuthFailure(correlationId, "Authentication Failed");
                        throw new SecurityException("Authentication failed for channel: " + request.getChannel());
                    }
                    // Get connector and process asynchronously
                    /*Connector connector = connectorFactory.getConnector(req.getChannel());
                    try {
                        return connector.processAsync(req);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }*/

                    return handlerRegistry.getHandlerAsync(request.getChannel(), request.getOperation())
                            .thenCompose(handler -> {
                                if (handler == null) {
                                    // Return a completed future with error response instead of null
                                    return CompletableFuture.completedFuture(
                                            createErrorResponse(request, null, correlationId,
                                                    new Exception("Unsupported channel/operation: " + request.getChannel() + "/" + request.getOperation()))
                                    );
                                }
                                return handler.handleAsync(request);
                            });
                })
                .thenApply(response -> {
                    // Parse response and store in Redis
                   // TransactionResponse response = parseConnectorResponseSimple(responsePayload, request, correlationId);
                    storeResponseInRedis(correlationId, response);

                    // Trigger async persistence
                    persistDataAsync(correlationId);

                    logger.info("Async transaction orchestration completed with status: {}",
                            response.isSuccess() ? "SUCCESS" : "FAILED");
                    return response;
                })
                .exceptionally(throwable -> {
                    logger.error("Async transaction orchestration failed", throwable);

                    TransactionResponse errorResponse = createErrorResponse(request, null, correlationId,
                            new RuntimeException(throwable));
                    storeResponseInRedis(correlationId, errorResponse);
                    persistDataAsync(correlationId);

                    return errorResponse;
                })
                .orTimeout(30, TimeUnit.SECONDS);
    }

    // Simplified response parsing without database dependency
    private TransactionResponse parseConnectorResponseSimple(String responsePayload,
                                                             TransactionRequest request,
                                                             String correlationId) {
        try {
            Map<String, Object> responseMap = objectMapper.readValue(responsePayload, Map.class);

            TransactionResponse response = new TransactionResponse();
            response.setChannel(request.getChannel());
            response.setOperation(request.getOperation());
            response.setCorrelationId(correlationId);

            Boolean success = (Boolean) responseMap.get("success");
            if (success == null) {
                success = checkSuccessIndicators(responseMap);
            }
            response.setSuccess(success);
            response.setPayload(responsePayload);

            // Extract key fields
            if (responseMap.containsKey("authorizationCode")) {
                response.addAdditionalData("authorizationCode", responseMap.get("authorizationCode"));
            }
            if (responseMap.containsKey("rrn")) {
                response.setExternalReference((String) responseMap.get("rrn"));
            }
            if (!success) {
                response.setErrorCode((String) responseMap.get("errorCode"));
                response.setErrorMessage((String) responseMap.get("errorMessage"));
            }

            return response;

        } catch (Exception e) {
            logger.error("Failed to parse connector response", e);

            TransactionResponse errorResponse = new TransactionResponse();
            errorResponse.setChannel(request.getChannel());
            errorResponse.setOperation(request.getOperation());
            errorResponse.setCorrelationId(correlationId);
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage("Failed to parse connector response: " + e.getMessage());
            errorResponse.setErrorCode("RESPONSE_PARSE_ERROR");
            errorResponse.setPayload(responsePayload);

            return errorResponse;
        }
    }

    private void storeRequestInRedis(String correlationId, TransactionRequest request,
                                     String clientId, String clientSecret, String token) {
        try {
            Map<String, Object> requestData = Map.of(
                    "request", request,
                    "clientId", clientId != null ? clientId : "",
                    "clientSecret", clientSecret != null ? clientSecret : "",
                    "token", token != null ? token : "",
                    "timestamp", System.currentTimeMillis(),
                    "status", "RECEIVED"
            );

            String jsonData = objectMapper.writeValueAsString(requestData);
            String key = "txn:request:" + correlationId;

            redisTemplate.opsForValue().set(key, jsonData, Duration.ofHours(24));
            logger.debug("Stored request in Redis for correlation: {}", correlationId);

        } catch (Exception e) {
            logger.error("Failed to store request in Redis for correlation: {}", correlationId, e);
        }
    }

    // Store response data as JSON string
    private void storeResponseInRedis(String correlationId, TransactionResponse response) {
        try {
            Map<String, Object> responseData = Map.of(
                    "response", response,
                    "timestamp", System.currentTimeMillis(),
                    "status", response.isSuccess() ? "SUCCESS" : "FAILED"
            );

            String jsonData = objectMapper.writeValueAsString(responseData);
            String key = "txn:response:" + correlationId;

            redisTemplate.opsForValue().set(key, jsonData, Duration.ofHours(24));
            logger.debug("Stored response in Redis for correlation: {}", correlationId);

        } catch (Exception e) {
            logger.error("Failed to store response in Redis for correlation: {}", correlationId, e);
        }
    }

    // Update Redis with authentication failure
    private void updateRedisWithAuthFailure(String correlationId, String errorMessage) {
        try {
            String requestKey = "txn:request:" + correlationId;
            String jsonData = (String) redisTemplate.opsForValue().get(requestKey);

            if (jsonData != null) {
                Map<String, Object> requestData = objectMapper.readValue(jsonData, Map.class);
                requestData.put("status", "AUTH_FAILED");
                requestData.put("errorMessage", errorMessage);
                requestData.put("authFailureTimestamp", System.currentTimeMillis());

                String updatedJson = objectMapper.writeValueAsString(requestData);
                redisTemplate.opsForValue().set(requestKey, updatedJson, Duration.ofHours(24));
                logger.debug("Updated Redis with auth failure for correlation: {}", correlationId);
            }
        } catch (Exception e) {
            logger.error("Failed to update Redis with auth failure for correlation: {}", correlationId, e);
        }
    }

    // Optimized async persistence with JSON string handling
    @Async("databaseTaskExecutor")
    private void persistDataAsync(String correlationId) {
        try {
            Thread.sleep(100); // Small delay to ensure Redis operations complete

            String requestKey = "txn:request:" + correlationId;
            String responseKey = "txn:response:" + correlationId;

            String requestJson = (String) redisTemplate.opsForValue().get(requestKey);
            String responseJson = (String) redisTemplate.opsForValue().get(responseKey);

            if (requestJson == null) {
                logger.error("Missing request data in Redis for correlation: {}", correlationId);
                return;
            }

            if (responseJson == null) {
                logger.error("Missing response data in Redis for correlation: {}", correlationId);
                return;
            }

            // Parse JSON data
            Map<String, Object> requestData = objectMapper.readValue(requestJson, Map.class);
            Map<String, Object> responseData = objectMapper.readValue(responseJson, Map.class);

            // Extract objects
            Map<String, Object> requestMap = (Map<String, Object>) requestData.get("request");
            Map<String, Object> responseMap = (Map<String, Object>) responseData.get("response");

            // Convert back to objects
            TransactionRequest request = objectMapper.convertValue(requestMap, TransactionRequest.class);
            TransactionResponse response = objectMapper.convertValue(responseMap, TransactionResponse.class);

            // Create transaction header
            TransactionHeader header = new TransactionHeader();
            header.setChannel(request.getChannel());
            header.setOperation(request.getOperation());
            header.setCorrelationId(correlationId);

            // Use timestamps from Redis data
            Long requestTimestamp = ((Number) requestData.get("timestamp")).longValue();
            Long responseTimestamp = ((Number) responseData.get("timestamp")).longValue();

            header.setRequestTimestamp(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(requestTimestamp), ZoneId.systemDefault()));
            header.setResponseTimestamp(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(responseTimestamp), ZoneId.systemDefault()));

            header.setStatus((String) responseData.get("status"));

            if (request.getPayload() != null) {
                header.setTransactionType(request.getPayload().getTransactionType());
                header.setAmount(request.getPayload().getAmount());
                header.setCurrency(request.getPayload().getCurrency());
                header.setMerchantId(request.getPayload().getMerchantId());
                header.setTerminalId(request.getPayload().getTerminalId());
            }

            if (!response.isSuccess()) {
                header.setErrorMessage(response.getErrorMessage());
                header.setErrorCode(response.getErrorCode());
            }

            // Save to database
            TransactionHeader savedHeader = transactionHeaderRepository.save(header);

            // Save channel-specific details
            try {
                channelSpecificTransactionService.saveChannelSpecificDetails(savedHeader, request, response);
            } catch (Exception e) {
                logger.error("Failed to save channel-specific details for correlation: {}", correlationId, e);
            }

            // Update Redis with transaction ID
            responseData.put("transactionId", savedHeader.getId());
            String updatedResponseJson = objectMapper.writeValueAsString(responseData);
            redisTemplate.opsForValue().set(responseKey, updatedResponseJson, Duration.ofHours(24));

            logger.debug("Async persistence completed for correlation: {}", correlationId);

            // Clean up request data after successful persistence
            redisTemplate.delete(requestKey);

        } catch (Exception e) {
            logger.error("Async persistence failed for correlation: {}", correlationId, e);
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
                                                       TransactionHeader header) throws JsonProcessingException {
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
           /* ObjectMapper mapper = new ObjectMapper();
            ResponsePayload errorPayload = mapper.readValue(responsePayload, ResponsePayload.class);
            response.setPayload(errorPayload);*/
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
           /* ObjectMapper mapper = new ObjectMapper();
            ResponsePayload errorPayload = mapper.readValue(responsePayload, ResponsePayload.class);
            errorResponse.setPayload(errorPayload);*/
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
        // errorResponse.setTransactionId(header.getId());
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
        return switch (channel.toUpperCase()) {
            case "BBPS" -> Duration.ofSeconds(3);
            case "ISO8583", "POS", "ATM" -> Duration.ofSeconds(5);
            case "UPI" -> Duration.ofSeconds(6);
            case "PG" -> Duration.ofSeconds(8);
            default -> Duration.ofSeconds(10);
        };
    }

    // Helper class for passing data through the async chain
    private static class ProcessingResult {
        final TransactionHeader header;
        final String responsePayload;

        ProcessingResult(TransactionHeader header, String responsePayload) {
            this.header = header;
            this.responsePayload = responsePayload;
        }
    }


}