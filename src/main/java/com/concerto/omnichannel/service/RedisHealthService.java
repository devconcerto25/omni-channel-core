package com.concerto.omnichannel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedisHealthService {

    private static final Logger logger = LoggerFactory.getLogger(RedisHealthService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private static final String HEALTH_CHECK_KEY = "health:check:";
    private static final String HEALTH_CHECK_VALUE = "ping";
    private static final int TIMEOUT_SECONDS = 5;

    /**
     * Comprehensive Redis health check
     */
    public Map<String, Object> checkRedisHealth() {
        Map<String, Object> healthStatus = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // Test 1: Basic connectivity
            boolean isConnected = checkConnection();
            healthStatus.put("connected", isConnected);

            if (!isConnected) {
                healthStatus.put("status", "DOWN");
                healthStatus.put("error", "Failed to connect to Redis");
                return healthStatus;
            }

            // Test 2: Read/Write operations
            boolean canReadWrite = checkReadWriteOperations();
            healthStatus.put("readWriteOperational", canReadWrite);

            // Test 3: Get Redis info
            Map<String, String> redisInfo = getRedisInfo();
            healthStatus.put("redisInfo", redisInfo);

            // Test 4: Memory usage
            Map<String, Object> memoryInfo = getMemoryInfo();
            healthStatus.put("memory", memoryInfo);

            // Test 5: Response time
            long responseTime = System.currentTimeMillis() - startTime;
            healthStatus.put("responseTimeMs", responseTime);

            // Determine overall status
            String overallStatus = determineOverallStatus(isConnected, canReadWrite, responseTime);
            healthStatus.put("status", overallStatus);
            healthStatus.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            logger.info("Redis health check completed: status={}, responseTime={}ms", overallStatus, responseTime);

        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            healthStatus.put("status", "DOWN");
            healthStatus.put("error", e.getMessage());
            healthStatus.put("connected", false);
        }

        return healthStatus;
    }

    /**
     * Basic connection test
     */
    public boolean checkConnection() {
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            String response = connection.ping();
            connection.close();

            boolean isConnected = "PONG".equals(response);
            logger.debug("Redis connection test: {}", isConnected ? "SUCCESS" : "FAILED");
            return isConnected;

        } catch (Exception e) {
            logger.warn("Redis connection test failed", e);
            return false;
        }
    }

    /**
     * Test read/write operations
     */
    public boolean checkReadWriteOperations() {
        try {
            String testKey = HEALTH_CHECK_KEY + System.currentTimeMillis();
            String testValue = HEALTH_CHECK_VALUE + "_" + System.currentTimeMillis();

            // Write test
            redisTemplate.opsForValue().set(testKey, testValue, TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Read test
            String retrievedValue = (String) redisTemplate.opsForValue().get(testKey);

            // Cleanup
            redisTemplate.delete(testKey);

            boolean success = testValue.equals(retrievedValue);
            logger.debug("Redis read/write test: {}", success ? "SUCCESS" : "FAILED");
            return success;

        } catch (Exception e) {
            logger.warn("Redis read/write test failed", e);
            return false;
        }
    }

    /**
     * Get Redis server information
     */
    public Map<String, String> getRedisInfo() {
        Map<String, String> info = new HashMap<>();

        try {
            RedisConnection connection = redisConnectionFactory.getConnection();

            // Get server info
            String serverInfo = connection.info("server").toString();
            parseInfoSection(serverInfo, info, "server");

            // Get memory info
            String memoryInfo = connection.info("memory").toString();
            parseInfoSection(memoryInfo, info, "memory");

            // Get stats info
            String statsInfo = connection.info("stats").toString();
            parseInfoSection(statsInfo, info, "stats");

            connection.close();

        } catch (Exception e) {
            logger.warn("Failed to get Redis info", e);
            info.put("error", "Failed to retrieve Redis info: " + e.getMessage());
        }

        return info;
    }

    /**
     * Get memory usage information
     */
    public Map<String, Object> getMemoryInfo() {
        Map<String, Object> memoryInfo = new HashMap<>();

        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            String info = connection.info("memory").toString();
            connection.close();

            // Parse memory information
            String[] lines = info.split("\r\n");
            for (String line : lines) {
                if (line.contains("used_memory:")) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        long usedMemory = Long.parseLong(parts[1]);
                        memoryInfo.put("usedMemoryBytes", usedMemory);
                        memoryInfo.put("usedMemoryMB", usedMemory / (1024 * 1024));
                    }
                }
                if (line.contains("used_memory_peak:")) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        long peakMemory = Long.parseLong(parts[1]);
                        memoryInfo.put("peakMemoryBytes", peakMemory);
                        memoryInfo.put("peakMemoryMB", peakMemory / (1024 * 1024));
                    }
                }
                if (line.contains("maxmemory:")) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        long maxMemory = Long.parseLong(parts[1]);
                        memoryInfo.put("maxMemoryBytes", maxMemory);
                        if (maxMemory > 0) {
                            memoryInfo.put("maxMemoryMB", maxMemory / (1024 * 1024));
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to get Redis memory info", e);
            memoryInfo.put("error", "Failed to retrieve memory info");
        }

        return memoryInfo;
    }

    /**
     * Parse Redis INFO command output
     */
    private void parseInfoSection(String infoOutput, Map<String, String> info, String section) {
        String[] lines = infoOutput.split("\r\n");
        for (String line : lines) {
            if (line.contains(":") && !line.startsWith("#")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    info.put(section + "_" + parts[0], parts[1]);
                }
            }
        }
    }

    /**
     * Determine overall health status
     */
    private String determineOverallStatus(boolean isConnected, boolean canReadWrite, long responseTime) {
        if (!isConnected) {
            return "DOWN";
        }

        if (!canReadWrite) {
            return "DEGRADED";
        }

        if (responseTime > 1000) { // More than 1 second
            return "SLOW";
        }

        if (responseTime > 500) { // More than 500ms
            return "WARNING";
        }

        return "UP";
    }

    /**
     * Quick health check for monitoring
     */
    public boolean isRedisHealthy() {
        try {
            return checkConnection() && checkReadWriteOperations();
        } catch (Exception e) {
            logger.error("Redis health check failed", e);
            return false;
        }
    }

    /**
     * Get Redis connection count
     */
    /*public int getConnectionCount() {
        try {
            RedisConnection connection = redisConnectionFactory.getConnection();
            String clientList = connection.clientList();
            connection.close();

            // Count lines (each line represents a connection)
            return clientList.split("\n").length - 1; // -1 for empty last line

        } catch (Exception e) {
            logger.warn("Failed to get Redis connection count", e);
            return -1;
        }
    }*/

    /**
     * Test specific Redis operations used by the application
     */
    public Map<String, Boolean> testApplicationOperations() {
        Map<String, Boolean> results = new HashMap<>();

        // Test configuration caching (common use case)
        results.put("configurationCache", testConfigurationCache());

        // Test session storage (if used)
        results.put("sessionStorage", testSessionStorage());

        // Test rate limiting (if used)
        results.put("rateLimiting", testRateLimiting());

        return results;
    }

    private boolean testConfigurationCache() {
        try {
            String key = "test:config:health";
            Map<String, String> testConfig = Map.of("key1", "value1", "key2", "value2");

            redisTemplate.opsForHash().putAll(key, testConfig);
            Map<Object, Object> retrieved = redisTemplate.opsForHash().entries(key);
            redisTemplate.delete(key);

            return retrieved.size() == 2;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testSessionStorage() {
        try {
            String sessionKey = "test:session:" + System.currentTimeMillis();
            String sessionData = "test-session-data";

            redisTemplate.opsForValue().set(sessionKey, sessionData, 30, TimeUnit.SECONDS);
            String retrieved = (String) redisTemplate.opsForValue().get(sessionKey);
            redisTemplate.delete(sessionKey);

            return sessionData.equals(retrieved);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean testRateLimiting() {
        try {
            String rateLimitKey = "test:ratelimit:" + System.currentTimeMillis();

            // Test increment operation (common for rate limiting)
            Long count = redisTemplate.opsForValue().increment(rateLimitKey);
            redisTemplate.expire(rateLimitKey, 60, TimeUnit.SECONDS);
            redisTemplate.delete(rateLimitKey);

            return count != null && count == 1L;
        } catch (Exception e) {
            return false;
        }
    }
}
