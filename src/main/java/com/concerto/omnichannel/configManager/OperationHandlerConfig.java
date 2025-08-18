package com.concerto.omnichannel.configManager;


import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.registry.OperationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Bean;

import java.util.List;

@Configuration
public class OperationHandlerConfig {

    @Autowired
    private List<OperationHandler> handlers; // Automatically collects all beans implementing OperationHandler

    public OperationHandlerConfig(List<OperationHandler> handlers) {
        this.handlers = handlers;
    }

    @Bean
    public OperationRegistry operationRegistry() {
        //OperationHandlerRegistry registry = new OperationHandlerRegistry();
        OperationRegistry registry = new OperationRegistry();
        for (OperationHandler handler : handlers) {
            registry.register(handler.getChannel(),handler.getOperationType(), handler);
        }
        return registry;
    }
}
