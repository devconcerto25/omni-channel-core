package com.concerto.omnichannel.connector;

import com.concerto.omnichannel.configManager.ConnectorTimeoutConfig;
import com.concerto.omnichannel.service.ISO8583MessageParser;
import com.concerto.omnichannel.service.ExternalSwitchConnector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ExternalSwitchConnector switchConnector;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${iso8583.packager.type:ascii}")
    private String packagerType; // ascii or binary

    @Override
    public String process(String payload) throws Exception {
        logger.info("Processing ISO8583 payload with packager type: {}", packagerType);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<String> task = () -> processISO8583Message(payload);

        Future<String> future = executor.submit(task);
        try {
            int timeout = timeoutConfig.getTimeoutFor("ISO8583");
            String result = future.get(timeout, TimeUnit.MILLISECONDS);
            logger.info("ISO8583 processing completed successfully");
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            logger.error("ISO8583 Connector timed out after {} ms", timeoutConfig.getTimeoutFor("ISO8583"));
            throw new RuntimeException("ISO8583 Connector timed out");
        } catch (Exception e) {
            logger.error("Error processing ISO8583 message", e);
            throw new RuntimeException("ISO8583 processing failed: " + e.getMessage(), e);
        } finally {
            executor.shutdownNow();
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

    private String processISO8583Message(String payload) throws Exception {
        try {
            // Parse JSON payload
            JsonNode jsonNode = objectMapper.readTree(payload);

            // Extract transaction request from JSON
            String channel = jsonNode.get("channel").asText();
            String operation = jsonNode.get("operation").asText();

            logger.debug("Processing {} operation for channel {}", operation, channel);

            // Convert JSON to ISO8583 message

            ISOMsg requestMsg = messageParser.jsonToISO8583FromJson(payload);

            // Send to external switch
            ISOMsg responseMsg = switchConnector.sendToSwitchSync(requestMsg, channel);

            // Convert ISO8583 response back to JSON
            Map<String, Object> jsonResponse = messageParser.iso8583ToJson(responseMsg);

            // Return JSON response
            return objectMapper.writeValueAsString(jsonResponse);

        } catch (Exception e) {
            logger.error("Failed to process ISO8583 message", e);

            // Return error response
            Map<String, Object> errorResponse = Map.of(
                    "success", false,
                    "errorCode", "PROCESSING_ERROR",
                    "errorMessage", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            );

            return objectMapper.writeValueAsString(errorResponse);
        }
    }
}