package com.concerto.omnichannel.controller;

import com.concerto.omnichannel.service.AsyncExternalSwitchConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
@RestController
@RequestMapping("/api/monitoring")
public class ConnectionPoolMonitoringController {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolMonitoringController.class);

    @Autowired
    private AsyncExternalSwitchConnector switchConnector;

    @GetMapping("/connection-pools")
    public ResponseEntity<Map<String, Object>> getConnectionPoolStatus() {
        Map<String, Object> status = new HashMap<>();

        // Get pool statistics from your connector
        Map<String, PoolStatistics> poolStats = switchConnector.getPoolStatistics();

        status.put("totalPools", poolStats.size());
        status.put("pools", poolStats);
        status.put("timestamp", Instant.now());

        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            Map<String, PoolStatistics> poolStats = switchConnector.getPoolStatistics();

            boolean healthy = poolStats.values().stream()
                    .allMatch(stats -> stats.getAvailableConnections() > 0 || stats.getActiveConnections() < stats.getMaxConnections());

            health.put("status", healthy ? "UP" : "DOWN");
            health.put("poolsHealthy", healthy);
            health.put("totalPools", poolStats.size());

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }

    // Statistics class for pool monitoring
    public static class PoolStatistics {
        private final String channelId;
        private final int maxConnections;
        private final int activeConnections;
        private final int availableConnections;
        private final long totalRequests;
        private final long failedRequests;

        public PoolStatistics(String channelId, int maxConnections, int activeConnections,
                              int availableConnections, long totalRequests, long failedRequests) {
            this.channelId = channelId;
            this.maxConnections = maxConnections;
            this.activeConnections = activeConnections;
            this.availableConnections = availableConnections;
            this.totalRequests = totalRequests;
            this.failedRequests = failedRequests;
        }

        // Getters
        public String getChannelId() { return channelId; }
        public int getMaxConnections() { return maxConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getAvailableConnections() { return availableConnections; }
        public long getTotalRequests() { return totalRequests; }
        public long getFailedRequests() { return failedRequests; }

        public double getSuccessRate() {
            return totalRequests > 0 ? (double) (totalRequests - failedRequests) / totalRequests * 100 : 0;
        }

        public double getUtilization() {
            return maxConnections > 0 ? (double) activeConnections / maxConnections * 100 : 0;
        }
    }
}
