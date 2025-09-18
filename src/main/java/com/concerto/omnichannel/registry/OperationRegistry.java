package com.concerto.omnichannel.registry;


import com.concerto.omnichannel.operations.OperationHandler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class OperationRegistry {

    // Key: channel + ":" + operation (e.g., "BBPS:FETCH_BILL")
    private final Map<String, OperationHandler> handlerMap = new HashMap<>();

    public void register(String channel, String operation, OperationHandler handler) {
        handlerMap.put(getKey(channel, operation), handler);
    }

    public OperationHandler getHandler(String channel, String operation) {
        return handlerMap.get(getKey(channel, operation));
    }

    private String getKey(String channel, String operation) {
        return channel.toUpperCase() + ":" + operation.toUpperCase();
    }

    public CompletableFuture<OperationHandler> getHandlerAsync(String channel, String operation) {
        OperationHandler handler = handlerMap.get(getKey(channel, operation));
        return CompletableFuture.completedFuture(handler);
    }
}

