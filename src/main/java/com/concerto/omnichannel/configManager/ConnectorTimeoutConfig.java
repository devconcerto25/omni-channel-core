package com.concerto.omnichannel.configManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ConnectorTimeoutConfig {

    @Value("${connector.timeouts.ISO8583:5000}")
    private int iso8583Timeout;

    @Value("${connector.timeouts.BBPS:3000}")
    private int bbpsTimeout;

    @Value("${connector.timeouts.UPI:6000}")
    private int upiTimeout;

    @Value("${connector.timeouts.PG:8000}")
    private int pgTimeout;

    @Value("${connector.timeouts.default:10000}")
    private int defaultTimeout;

    private final Map<String, Integer> timeoutCache = new HashMap<>();

    public int getTimeoutFor(String connectorType) {
        return timeoutCache.computeIfAbsent(connectorType, key -> {
            switch (key.toUpperCase()) {
                case "ISO8583":
                    return iso8583Timeout;
                case "BBPS":
                    return bbpsTimeout;
                case "UPI":
                    return upiTimeout;
                case "PG":
                    return pgTimeout;
                default:
                    return defaultTimeout;
            }
        });
    }

    public void updateTimeout(String connectorType, int timeout) {
        timeoutCache.put(connectorType, timeout);
    }
}


