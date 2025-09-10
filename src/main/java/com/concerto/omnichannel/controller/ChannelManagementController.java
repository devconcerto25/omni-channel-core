package com.concerto.omnichannel.controller;


import com.concerto.omnichannel.dto.ApiResponse;
import com.concerto.omnichannel.service.ChannelSpecificTransactionService;
import com.concerto.omnichannel.service.TransactionAnalyticsService;
import com.concerto.omnichannel.entity.TransactionHeader;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions/channels")
public class ChannelManagementController {

    @Autowired
    private ChannelSpecificTransactionService channelSpecificService;

    @Autowired
    private TransactionAnalyticsService analyticsService;

    /**
     * Get channel-specific transaction details
     */
    @GetMapping("/{channel}/transaction/{correlationId}")
    public ResponseEntity<ApiResponse<Object>> getChannelTransactionDetails(
            @PathVariable String channel,
            @PathVariable String correlationId) {

        try {
            // First get the transaction header
            TransactionHeader header = getTransactionHeaderByCorrelationId(correlationId);
            if (header == null) {
                return ResponseEntity.notFound().build();
            }

            // Validate channel matches
            if (!channel.equalsIgnoreCase(header.getChannel())) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Channel mismatch", UUID.randomUUID().toString())
                );
            }

            // Get channel-specific details
            Object channelDetails = channelSpecificService.getChannelSpecificDetails(header);

            return ResponseEntity.ok(
                    ApiResponse.success(channelDetails, "Channel-specific details retrieved", correlationId)
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("Failed to retrieve channel details: " + e.getMessage(),
                            UUID.randomUUID().toString())
            );
        }
    }

    /**
     * Search transactions by channel-specific criteria
     */
    @GetMapping("/{channel}/search")
    public ResponseEntity<ApiResponse<List<?>>> searchChannelTransactions(
            @PathVariable String channel,
            @RequestParam String searchType,
            @RequestParam String searchValue) {

        try {
            List<?> results = channelSpecificService.searchByChannelSpecificCriteria(
                    channel, searchType, searchValue);

            return ResponseEntity.ok(
                    ApiResponse.success(results,
                            "Search completed for " + channel + " transactions",
                            UUID.randomUUID().toString())
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("Search failed: " + e.getMessage(),
                            UUID.randomUUID().toString())
            );
        }
    }

    /**
     * Get POS-specific transaction analytics
     */
    @GetMapping("/POS/analytics/merchant-performance")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPOSMerchantPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate) {

        try {
            List<Map<String, Object>> performance = analyticsService.getPOSMerchantPerformance(startDate);

            return ResponseEntity.ok(
                    ApiResponse.success(performance, "POS merchant performance retrieved",
                            UUID.randomUUID().toString())
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("Failed to get merchant performance: " + e.getMessage(),
                            UUID.randomUUID().toString())
            );
        }
    }

    /**
     * Get UPI transaction patterns
     */
    @GetMapping("/UPI/analytics/transaction-patterns")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUPITransactionPatterns(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate) {

        try {
            List<Map<String, Object>> patterns = analyticsService.getUPITransactionPatterns(startDate);

            return ResponseEntity.ok(
                    ApiResponse.success(patterns, "UPI transaction patterns retrieved",
                            UUID.randomUUID().toString())
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("Failed to get UPI patterns: " + e.getMessage(),
                            UUID.randomUUID().toString())
            );
        }
    }

    /**
     * Get cross-channel summary analytics
     */
    @GetMapping("/analytics/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getChannelSummary(
            @RequestParam
            String channel,

            @Parameter(
                    name = "startDate",
                    description = "Start date for transaction search in ISO 8601 format",
                    example = "2024-01-15T10:30:00.000+05:30",
                    schema = @Schema(type = "string", format = "date-time")
            )
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @Parameter(
                    name = "endDate",
                    description = "End date for transaction search in ISO 8601 format",
                    example = "2024-01-15T10:30:00.000+05:30",
                    schema = @Schema(type = "string", format = "date-time")
            )
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate) {

        try {
            List<Map<String, Object>> summary = analyticsService.getChannelSummary(channel, startDate, endDate);

            return ResponseEntity.ok(
                    ApiResponse.success(summary, "Channel summary retrieved",
                            UUID.randomUUID().toString())
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("Failed to get channel summary: " + e.getMessage(),
                            UUID.randomUUID().toString())
            );
        }
    }

    // Helper method - you'll need to implement this based on your existing service
    private TransactionHeader getTransactionHeaderByCorrelationId(String correlationId) {
        // Implementation depends on your existing TransactionService
        // return transactionService.findByCorrelationId(correlationId);
        return null; // Placeholder
    }
}

