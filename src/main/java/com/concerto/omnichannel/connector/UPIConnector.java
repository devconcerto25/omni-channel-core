package com.concerto.omnichannel.connector;

import com.concerto.omnichannel.configManager.ConnectorTimeoutConfig;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component("UPI")
public class UPIConnector implements Connector {

    private static final Logger logger = LoggerFactory.getLogger(UPIConnector.class);

    @Autowired
    private ConnectorTimeoutConfig timeoutConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String process(TransactionRequest request) throws Exception {
        logger.info("Processing UPI payload");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<String> task = () -> processUPIMessage(request);

        Future<String> future = executor.submit(task);
        try {
            int timeout = timeoutConfig.getTimeoutFor("UPI");
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            logger.error("UPI Connector timed out");
            throw new RuntimeException("UPI Connector timed out");
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public CompletableFuture<String> processAsync(TransactionRequest request) throws Exception {
        return null;
    }

    @Override
    public boolean supports(String channel) {
        return "UPI".equalsIgnoreCase(channel);
    }

    @Override
    public String getConnectorType() {
        return "UPI_HTTP";
    }

    private String processUPIMessage(TransactionRequest request) throws Exception {
        // Simulate UPI processing
        logger.debug("Processing UPI payment request");

        // In real implementation, this would:
        // 1. Parse UPI specific JSON format
        // 2. Call UPI APIs (NPCI/PSP)
        // 3. Handle UPI responses
        // 4. Return standardized response

        Thread.sleep(2000); // Simulate processing time

        Map<String, Object> response = Map.of(
                "success", true,
                "upiTransactionId", "UPI" + System.currentTimeMillis(),
                "status", "SUCCESS",
                "amount", "100.00",
                "timestamp", System.currentTimeMillis()
        );

        return objectMapper.writeValueAsString(response);
    }
}
