package com.concerto.omnichannel.service;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.entity.TransactionHeader;
import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.registry.OperationHandlerRegistry;
import com.concerto.omnichannel.repository.TransactionHeaderRepository;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
public class MainOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(MainOrchestrator.class);

    private final AuthenticationService authenticationService;
    private final OperationHandlerRegistry operationHandlerRegistry;
    private final TransactionHeaderRepository transactionHeaderRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    @Autowired
    public MainOrchestrator(AuthenticationService authenticationService,
                            OperationHandlerRegistry operationHandlerRegistry,
                            TransactionHeaderRepository transactionHeaderRepository,
                            CircuitBreakerRegistry circuitBreakerRegistry,
                            RetryRegistry retryRegistry,
                            TimeLimiterRegistry timeLimiterRegistry) {
        this.authenticationService = authenticationService;
        this.operationHandlerRegistry = operationHandlerRegistry;
        this.transactionHeaderRepository = transactionHeaderRepository;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

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

        logger.info("Starting transaction orchestration for channel: {} operation: {}",
                request.getChannel(), request.getOperation());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Set MDC in async context
                MDC.put("correlationId", correlationId);
                MDC.put("channel", request.getChannel());

                return orchestrateInternal(request, clientId, clientSecret, token, correlationId);

            } catch (Exception e) {
                logger.error("Orchestration failed with error", e);
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
            // 2. Authentication with circuit breaker
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

            // 3. Get operation handler with caching
            OperationHandler handler = getOperationHandlerWithCache(request.getChannel(), request.getOperation());

            if (handler == null) {
                header.setStatus("HANDLER_NOT_FOUND");
                header.setErrorMessage("Unsupported channel or operation");
                header.setResponseTimestamp(LocalDateTime.now());
                transactionHeaderRepository.save(header);

                throw new IllegalArgumentException("Unsupported channel: " + request.getChannel() +
                        " or operation: " + request.getOperation());
            }

            // 4. Execute handler with resilience patterns
            header.setStatus("PROCESSING");
            transactionHeaderRepository.save(header);

            TransactionResponse response = executeWithResilience(
                    request.getChannel() + "-" + request.getOperation(),
                    () -> handler.handle(request),
                    getTimeoutForChannel(request.getChannel())
            );

            // 5. Update final status
            header.setStatus("SUCCESS");
            header.setResponseTimestamp(LocalDateTime.now());
            transactionHeaderRepository.save(header);

            response.setCorrelationId(correlationId);
            response.setTransactionId(header.getId());

            logger.info("Transaction orchestration completed successfully");
            return response;

        } catch (Exception e) {
            // Update transaction status on failure
            header.setStatus("FAILED");
            header.setErrorMessage(e.getMessage());
            header.setResponseTimestamp(LocalDateTime.now());
            transactionHeaderRepository.save(header);

            logger.error("Transaction orchestration failed", e);

            // Create error response
            TransactionResponse errorResponse = new TransactionResponse();
            errorResponse.setCorrelationId(correlationId);
            errorResponse.setTransactionId(header.getId());
            errorResponse.setChannel(request.getChannel());
            errorResponse.setSuccess(false);
            errorResponse.setErrorMessage(e.getMessage());

            return errorResponse;
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
        }

        return transactionHeaderRepository.save(header);
    }

    @Cacheable(value = "operationHandlers", key = "#channel + '-' + #operation")
    private OperationHandler getOperationHandlerWithCache(String channel, String operation) {
        return operationHandlerRegistry.getHandler(channel, operation);
    }

    private <T> T executeWithResilience(String name, Supplier<T> supplier, Duration timeout) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        Retry retry = retryRegistry.retry(name);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(name);

        Supplier<CompletableFuture<T>> decoratedSupplier = (Supplier<CompletableFuture<T>>) TimeLimiter
                .decorateFutureSupplier(timeLimiter, () ->
                        CompletableFuture.supplyAsync(supplier));

        decoratedSupplier = CircuitBreaker
                .decorateSupplier(circuitBreaker, decoratedSupplier);

        decoratedSupplier = Retry
                .decorateSupplier(retry, decoratedSupplier);

        try {
            return decoratedSupplier.get().join();
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
                return Duration.ofSeconds(5);
            case "UPI":
                return Duration.ofSeconds(6);
            default:
                return Duration.ofSeconds(10);
        }
    }
}