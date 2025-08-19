package com.concerto.omnichannel.connector;

import com.concerto.omnichannel.configManager.ConnectorTimeoutConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component("BBPS")
public class BBPSConnector implements Connector {

    private static final Logger logger = LoggerFactory.getLogger(BBPSConnector.class);

    @Autowired
    private ConnectorTimeoutConfig timeoutConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String process(String payload) throws Exception {
        logger.info("Processing BBPS payload");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<String> task = () -> processBBPSMessage(payload);

        Future<String> future = executor.submit(task);
        try {
            int timeout = timeoutConfig.getTimeoutFor("BBPS");
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            logger.error("BBPS Connector timed out");
            throw new RuntimeException("BBPS Connector timed out");
        } finally {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean supports(String channel) {
        return "BBPS".equalsIgnoreCase(channel);
    }

    @Override
    public String getConnectorType() {
        return "BBPS_HTTP";
    }

    private String processBBPSMessage(String payload) throws Exception {
        // Simulate BBPS processing
        logger.debug("Processing BBPS bill payment request");

        // In real implementation, this would:
        // 1. Parse BBPS specific JSON format
        // 2. Call BBPS API endpoints
        // 3. Handle BBPS responses
        // 4. Return standardized response

        Thread.sleep(1500); // Simulate processing time

        Map<String, Object> response = Map.of(
                "success", true,
                "billStatus", "PAID",
                "transactionId", "BBPS" + System.currentTimeMillis(),
                "timestamp", System.currentTimeMillis()
        );

        return objectMapper.writeValueAsString(response);
    }
}
