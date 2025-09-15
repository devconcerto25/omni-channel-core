package com.concerto.omnichannel.connector;

import com.concerto.omnichannel.configManager.ConnectorTimeoutConfig;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.service.AsyncExternalSwitchConnector;
import com.concerto.omnichannel.service.ISO8583MessageParser;
import com.concerto.omnichannel.service.STANGenerationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component("ISO8583")
public class ISO8583Connector implements Connector {

    private static final Logger logger = LoggerFactory.getLogger(ISO8583Connector.class);

    @Autowired
    private ConnectorTimeoutConfig timeoutConfig;

    @Autowired
    private ISO8583MessageParser messageParser;

    @Autowired
    private AsyncExternalSwitchConnector asyncSwitchConnector;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private STANGenerationService stanService;

    @Value("${iso8583.packager.type:ascii}")
    private String packagerType;

    private static final String STAN_FORMAT = "%06d";

    // Shared thread pool for async operations - properly sized for 100 TPS
    private final ExecutorService sharedExecutor = Executors.newFixedThreadPool(50);

    @Override
    public String process(TransactionRequest request) throws Exception {
        logger.debug("Processing ISO8583 payload synchronously");

        // For sync processing, still use the optimized async method but wait for result
        try {
            return processISO8583MessageAsync(request)
                    .get(timeoutConfig.getTimeoutFor("ISO8583"), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            logger.error("ISO8583 Connector timed out after {} ms", timeoutConfig.getTimeoutFor("ISO8583"));
            throw new RuntimeException("ISO8583 Connector timed out");
        } catch (ExecutionException e) {
            logger.error("Error processing ISO8583 message", e.getCause());
            throw new RuntimeException("ISO8583 processing failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    @Override
    public CompletableFuture<String> processAsync(TransactionRequest request) {
        logger.debug("Processing ISO8583 payload asynchronously");

        return processISO8583MessageAsync(request)
                .orTimeout(timeoutConfig.getTimeoutFor("ISO8583"), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        logger.error("ISO8583 Connector timed out after {} ms",
                                timeoutConfig.getTimeoutFor("ISO8583"));
                        return createErrorResponse("TIMEOUT", "ISO8583 Connector timed out");
                    }
                    logger.error("Error processing ISO8583 message", throwable);
                    return createErrorResponse("PROCESSING_ERROR",
                            "ISO8583 processing failed: " + throwable.getMessage());
                });
    }


    private CompletableFuture<String> processISO8583MessageAsync(TransactionRequest request) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // Parse JSON payload - this is fast, no I/O
                        String payload = objectMapper.writeValueAsString(request);
                        JsonNode jsonNode = objectMapper.readTree(payload);
                        String channel = jsonNode.get("channel").asText();

                        logger.debug("Processing async operation for channel {}", channel);

                        // Convert JSON to ISO8583 message - also fast, no I/O
                        ISOMsg requestMsg = messageParser.jsonToISO8583FromJson(payload);

                        // Return the data needed for async processing
                        return new ProcessingContext(requestMsg, channel, jsonNode);

                    } catch (Exception e) {
                        logger.error("Failed to prepare ISO8583 message", e);
                        throw new RuntimeException("Failed to prepare message", e);
                    }
                }, sharedExecutor)

                // Chain the async switch call - THIS IS THE CRITICAL PART
                .thenCompose(context -> {
                    // Use connection pooling for maximum performance
                    return asyncSwitchConnector.sendToSwitchWithPool(context.requestMsg, context.channel)
                            .thenApply(isoResponse -> new ResponseContext(context, isoResponse))
                            .exceptionally(throwable -> {
                                logger.error("Switch communication failed for channel: {}", context.channel, throwable);
                                throw new RuntimeException("Switch communication failed", throwable);
                            });
                })

                // Process the response - back to CPU-bound work
                .thenApplyAsync(responseContext -> {
                    try {
                        if (responseContext.isoResponse == null) {
                            logger.warn("No ISO8583 response for channel {}", responseContext.context.channel);
                            return createErrorResponse("NO_RESPONSE", "No ISO8583 response received");
                        }

                        // Convert ISO8583 response back to JSON
                        Map<String, Object> jsonResponse = messageParser.iso8583ToJson(responseContext.isoResponse);

                        // Add success indicators
                        if (!jsonResponse.containsKey("success")) {
                            // Determine success based on response code
                            String responseCode = (String) jsonResponse.get("responseCode");
                            jsonResponse.put("success", "00".equals(responseCode));
                        }

                        logger.debug("Transaction completed successfully for channel: {}",
                                responseContext.context.channel);

                        return objectMapper.writeValueAsString(jsonResponse);

                    } catch (Exception e) {
                        logger.error("Error processing response", e);
                        return createErrorResponse("RESPONSE_PROCESSING_ERROR",
                                "Error processing response: " + e.getMessage());
                    }
                }, sharedExecutor);
    }

    /**
     * Helper classes for better async processing
     */
    private static class ProcessingContext {
        final ISOMsg requestMsg;
        final String channel;
        final JsonNode jsonNode;

        ProcessingContext(ISOMsg requestMsg, String channel, JsonNode jsonNode) {
            this.requestMsg = requestMsg;
            this.channel = channel;
            this.jsonNode = jsonNode;
        }
    }

    private static class ResponseContext {
        final ProcessingContext context;
        final ISOMsg isoResponse;

        ResponseContext(ProcessingContext context, ISOMsg isoResponse) {
            this.context = context;
            this.isoResponse = isoResponse;
        }
    }

    /**
     * Helper method to create consistent error responses
     */
    private String createErrorResponse(String errorCode, String errorMessage) {
        try {
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "errorCode", errorCode,
                    "errorMessage", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );
            return objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            // Fallback if JSON serialization fails
            return String.format("{\"success\":false,\"errorCode\":\"%s\",\"errorMessage\":\"%s\"}",
                    errorCode, errorMessage.replace("\"", "\\\""));
        }
    }

    @Override
    public boolean supports(String channel) {
        return "ISO8583".equalsIgnoreCase(channel) ||
                "ATM".equalsIgnoreCase(channel) ||
                "POS".equalsIgnoreCase(channel);
    }

    @Override
    public String getConnectorType() {
        return "ISO8583_" + packagerType.toUpperCase();
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down ISO8583Connector thread pool");
        sharedExecutor.shutdown();
        try {
            if (!sharedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sharedExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sharedExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}