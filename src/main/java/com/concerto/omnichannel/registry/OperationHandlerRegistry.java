package com.concerto.omnichannel.registry;

import com.concerto.omnichannel.operations.OperationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OperationHandlerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(OperationHandlerRegistry.class);

    private final Map<String, OperationHandler> handlerCache = new ConcurrentHashMap<>();

    @Autowired
    private List<OperationHandler> operationHandlers;

    /**
     * Get operation handler for the specified channel and operation
     */
    public OperationHandler getHandler(String channel, String operation) {
        String key = channel + "-" + operation;

        // Check cache first
        OperationHandler cachedHandler = handlerCache.get(key);
        if (cachedHandler != null) {
            return cachedHandler;
        }

        // Find appropriate handler
        for (OperationHandler handler : operationHandlers) {
            if (handler.supports(channel, operation)) {
                handlerCache.put(key, handler);
                logger.info("Handler found for {}-{}: {}", channel, operation, handler.getClass().getSimpleName());
                return handler;
            }
        }

        logger.warn("No handler found for channel: {} operation: {}", channel, operation);
        return null;
    }

    /**
     * Get all available handlers
     */
    public List<OperationHandler> getAllHandlers() {
        return operationHandlers;
    }

    /**
     * Check if handler exists for channel and operation
     */
    public boolean hasHandler(String channel, String operation) {
        return operationHandlers.stream()
                .anyMatch(handler -> handler.supports(channel, operation));
    }

    /**
     * Clear handler cache
     */
    public void clearCache() {
        handlerCache.clear();
        logger.info("Handler cache cleared");
    }
}
