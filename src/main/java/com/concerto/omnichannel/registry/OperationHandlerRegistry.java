package com.concerto.omnichannel.registry;

import com.concerto.omnichannel.operations.OperationHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class OperationHandlerRegistry {

    @Autowired
    private List<OperationHandler> handlers;
    private final Map<String, OperationHandler> registry = new HashMap<>();

    @PostConstruct
    public void init() {
        for (OperationHandler handler : handlers) {
            String key = handler.getChannel() + "/" + handler.getOperationType();  //
            registry.put(key, handler);
        }
    }

    public OperationHandler getHandler(String channel, String operation) {
        return registry.get(channel + "/" + operation);  //
    }
}

