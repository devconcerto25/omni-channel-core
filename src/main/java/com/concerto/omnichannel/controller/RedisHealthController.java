package com.concerto.omnichannel.controller;

import com.concerto.omnichannel.dto.ApiResponse;
import com.concerto.omnichannel.service.RedisHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitor")
@Tag(name = "Health Check", description = "System health monitoring APIs")
public class RedisHealthController {

    @Autowired
    private RedisHealthService redisHealthService;

    @GetMapping("/health/redis")
    @Operation(
            summary = "Comprehensive Redis Health Check",
            description = "Performs detailed Redis health check including connectivity, read/write operations, memory usage, and performance metrics"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Redis is healthy"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Redis is unhealthy or degraded")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRedisHealth() {
        try {
            Map<String, Object> healthStatus = redisHealthService.checkRedisHealth();

            String status = (String) healthStatus.get("status");
            if ("UP".equals(status)) {
                return ResponseEntity.ok(
                        ApiResponse.success(healthStatus, "Redis is healthy", UUID.randomUUID().toString())
                );
            } else {
                return ResponseEntity.status(503).body(
                        ApiResponse.success(healthStatus, "Redis health check completed", UUID.randomUUID().toString())
                );
            }

        } catch (Exception e) {
            return ResponseEntity.status(503).body(
                    ApiResponse.error("Redis health check failed: " + e.getMessage(), UUID.randomUUID().toString())
            );
        }
    }

    @GetMapping("/health/redis/quick")
    @Operation(
            summary = "Quick Redis Health Check",
            description = "Fast connectivity test for Redis - returns simple UP/DOWN status"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Redis connectivity check completed")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQuickRedisHealth() {
        try {
            boolean isHealthy = redisHealthService.isRedisHealthy();

            Map<String, Object> quickStatus = Map.of(
                    "status", isHealthy ? "UP" : "DOWN",
                    "healthy", isHealthy
            );

            return ResponseEntity.ok(
                    ApiResponse.success(quickStatus, isHealthy ? "Redis is healthy" : "Redis is unhealthy",
                            UUID.randomUUID().toString())
            );

        } catch (Exception e) {
            return ResponseEntity.status(503).body(
                    ApiResponse.error("Redis quick health check failed: " + e.getMessage(),
                            UUID.randomUUID().toString())
            );
        }
    }

    @GetMapping("/health/redis/operations")
    @Operation(
            summary = "Test Redis Operations",
            description = "Tests application-specific Redis operations like configuration caching, session storage, and rate limiting"
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Redis operations test completed")
    })
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> testRedisOperations() {
        try {
            Map<String, Boolean> operationResults = redisHealthService.testApplicationOperations();

            boolean allPassed = operationResults.values().stream().allMatch(Boolean::booleanValue);

            return ResponseEntity.ok(
                    ApiResponse.success(operationResults,
                            allPassed ? "All Redis operations successful" : "Some Redis operations failed",
                            UUID.randomUUID().toString())
            );

        } catch (Exception e) {
            return ResponseEntity.status(503).body(
                    ApiResponse.error("Redis operations test failed: " + e.getMessage(),
                            UUID.randomUUID().toString())
            );
        }
    }
}