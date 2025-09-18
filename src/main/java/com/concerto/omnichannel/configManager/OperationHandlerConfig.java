package com.concerto.omnichannel.configManager;


import com.concerto.omnichannel.handlers.iso8583.ISO8583RefundHandler;
import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.registry.OperationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Bean;

import java.util.List;

@Configuration
public class OperationHandlerConfig {
    private static final Logger logger = LoggerFactory.getLogger(OperationHandlerConfig.class);

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
            logger.debug("channel and operation registered {} {}", handler.getChannel(), handler.getOperationType());
        }
        return registry;
    }
}
