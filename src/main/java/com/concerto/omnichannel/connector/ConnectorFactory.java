package com.concerto.omnichannel.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorFactory.class);

    private final Map<String, Connector> connectorCache = new ConcurrentHashMap<>();

    @Autowired
    private List<Connector> connectors;

    /**
     * Get connector for the specified channel
     */
    public Connector getConnector(String channel) {
        // Check cache first
        Connector cachedConnector = connectorCache.get(channel);
        if (cachedConnector != null) {
            return cachedConnector;
        }

        // Find appropriate connector
        for (Connector connector : connectors) {
            if (connector.supports(channel)) {
                connectorCache.put(channel, connector);
                logger.info("Connector found for channel {}: {}", channel, connector.getConnectorType());
                return connector;
            }
        }

        logger.error("No connector found for channel: {}", channel);
        throw new IllegalArgumentException("Unsupported channel: " + channel);
    }

    /**
     * Get all available connectors
     */
    public List<Connector> getAllConnectors() {
        return connectors;
    }

    /**
     * Check if connector exists for channel
     */
    public boolean hasConnector(String channel) {
        return connectors.stream().anyMatch(connector -> connector.supports(channel));
    }

    /**
     * Clear connector cache
     */
    public void clearCache() {
        connectorCache.clear();
        logger.info("Connector cache cleared");
    }
}

