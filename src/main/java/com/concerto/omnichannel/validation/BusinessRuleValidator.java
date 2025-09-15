package com.concerto.omnichannel.validation;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.service.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.service.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Component
public class BusinessRuleValidator {

    private static final Logger logger = LoggerFactory.getLogger(BusinessRuleValidator.class);

    @Autowired
    private ConfigurationService configurationService;

    // Cached business rules to avoid repeated DB/config lookups
    private final ConcurrentHashMap<String, ChannelRules> channelRulesCache = new ConcurrentHashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_VALIDITY_MS = 300000; // 5 minutes

    // Pre-compiled validation rules for performance
    private static final BigDecimal DEFAULT_MIN_LIMIT = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_MAX_LIMIT = new BigDecimal("1000000");
    private static final BigDecimal DEFAULT_UPI_MAX = new BigDecimal("200000");
    private static final BigDecimal DEFAULT_ATM_DAILY = new BigDecimal("50000");

    /**
     * Fast validation with minimal external calls
     */
    public void validateBusinessRules(TransactionRequest request) {
        // For maximum performance during load testing, use simplified validation
        if (isHighLoadMode()) {
            validateBusinessRulesFast(request);
            return;
        }

        // Full validation for normal operations
        validateBusinessRulesFull(request);
    }

    /**
     * Ultra-fast validation for high-load scenarios
     */
    private void validateBusinessRulesFast(TransactionRequest request) {
        List<String> violations = new ArrayList<>();

        // Only critical validations with no external calls
        if (request.getPayload() != null && request.getPayload().getAmount() != null) {
            BigDecimal amount = request.getPayload().getAmount();

            // Hard-coded limits for performance
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                violations.add("Amount must be positive");
            }

            if (amount.compareTo(DEFAULT_MAX_LIMIT) > 0) {
                violations.add("Amount exceeds system limit");
            }

            // Channel-specific quick checks
            validateChannelSpecificFast(request, violations);
        }

        if (!violations.isEmpty()) {
            throw new RuntimeException("Business rule validation failed: " + String.join(", ", violations));
        }
    }

    /**
     * Full validation with caching for better performance
     */
    private void validateBusinessRulesFull(TransactionRequest request) {
        List<String> violations = new ArrayList<>();

        // Get cached channel rules
        ChannelRules rules = getCachedChannelRules(request.getChannel());

        // 1. Check if channel is active (cached)
        if (!rules.isActive()) {
            violations.add("Channel " + request.getChannel() + " is currently inactive");
        }

        // 2. Validate transaction limits (fast with cached values)
        validateTransactionLimitsCached(request, violations, rules);

        // 3. Validate business hours (fast local check)
        validateBusinessHoursCached(request, violations, rules);

        // 4. Channel-specific rules (optimized)
        validateChannelSpecificRulesCached(request, violations, rules);

        if (!violations.isEmpty()) {
            throw new RuntimeException("Business rule validation failed: " + String.join(", ", violations));
        }
    }

    /**
     * Get cached channel rules with periodic refresh
     */
    private ChannelRules getCachedChannelRules(String channel) {
        long currentTime = System.currentTimeMillis();

        // Check if cache needs refresh
        if (currentTime - lastCacheUpdate > CACHE_VALIDITY_MS) {
            refreshChannelRulesCache();
            lastCacheUpdate = currentTime;
        }

        return channelRulesCache.computeIfAbsent(channel, this::loadChannelRules);
    }

    /**
     * Load channel rules from configuration service
     */
    private ChannelRules loadChannelRules(String channel) {
        try {
            boolean isActive = configurationService.isChannelActive(channel);
            BigDecimal minLimit = configurationService.getConfigValue(channel, "minTransactionLimit", BigDecimal.class, DEFAULT_MIN_LIMIT);
            BigDecimal maxLimit = configurationService.getConfigValue(channel, "maxTransactionLimit", BigDecimal.class, DEFAULT_MAX_LIMIT);
            String startTime = configurationService.getConfigValue(channel, "businessHoursStart", "00:00");
            String endTime = configurationService.getConfigValue(channel, "businessHoursEnd", "23:59");

            return new ChannelRules(isActive, minLimit, maxLimit,
                    LocalTime.parse(startTime), LocalTime.parse(endTime));

        } catch (Exception e) {
            logger.warn("Failed to load rules for channel {}, using defaults", channel, e);
            return new ChannelRules(true, DEFAULT_MIN_LIMIT, DEFAULT_MAX_LIMIT,
                    LocalTime.MIDNIGHT, LocalTime.of(23, 59));
        }
    }

    /**
     * Refresh cache asynchronously
     */
    private void refreshChannelRulesCache() {
        CompletableFuture.runAsync(() -> {
            try {
                // Clear cache and let it reload on demand
                channelRulesCache.clear();
                logger.debug("Channel rules cache refreshed");
            } catch (Exception e) {
                logger.warn("Failed to refresh channel rules cache", e);
            }
        });
    }

    /**
     * Fast transaction limits validation with cached values
     */
    private void validateTransactionLimitsCached(TransactionRequest request, List<String> violations, ChannelRules rules) {
        if (request.getPayload() != null && request.getPayload().getAmount() != null) {
            BigDecimal amount = request.getPayload().getAmount();

            if (amount.compareTo(rules.getMinLimit()) < 0) {
                violations.add("Transaction amount below minimum limit of " + rules.getMinLimit());
            }

            if (amount.compareTo(rules.getMaxLimit()) > 0) {
                violations.add("Transaction amount exceeds maximum limit of " + rules.getMaxLimit());
            }
        }
    }

    /**
     * Fast business hours validation with cached values
     */
    private void validateBusinessHoursCached(TransactionRequest request, List<String> violations, ChannelRules rules) {
        LocalTime currentTime = LocalTime.now();

        if (currentTime.isBefore(rules.getBusinessStart()) || currentTime.isAfter(rules.getBusinessEnd())) {
            violations.add("Transaction outside business hours for channel " + request.getChannel());
        }
    }

    /**
     * Optimized channel-specific validation
     */
    private void validateChannelSpecificRulesCached(TransactionRequest request, List<String> violations, ChannelRules rules) {
        String channel = request.getChannel().toUpperCase();

        switch (channel) {
            case "ATM":
                validateAtmRulesFast(request, violations);
                break;
            case "POS":
                validatePosRulesFast(request, violations);
                break;
            case "UPI":
                validateUpiRulesFast(request, violations);
                break;
        }
    }

    /**
     * Fast channel-specific validations without external calls
     */
    private void validateChannelSpecificFast(TransactionRequest request, List<String> violations) {
        String channel = request.getChannel().toUpperCase();
        BigDecimal amount = request.getPayload().getAmount();

        switch (channel) {
            case "UPI":
                if (amount.compareTo(DEFAULT_UPI_MAX) > 0) {
                    violations.add("UPI transaction amount exceeds limit");
                }
                break;
            case "ATM":
                if ("withdrawal".equals(request.getOperation()) && amount.compareTo(DEFAULT_ATM_DAILY) > 0) {
                    violations.add("ATM withdrawal amount exceeds daily limit");
                }
                break;
            case "POS":
                if (request.getPayload().getMerchantId() == null) {
                    violations.add("Merchant ID is mandatory for POS transactions");
                }
                break;
        }
    }

    private void validateAtmRulesFast(TransactionRequest request, List<String> violations) {
        if ("withdrawal".equals(request.getOperation()) &&
                request.getPayload() != null &&
                request.getPayload().getAmount() != null) {

            if (request.getPayload().getAmount().compareTo(DEFAULT_ATM_DAILY) > 0) {
                violations.add("ATM withdrawal exceeds daily limit");
            }
        }
    }

    private void validatePosRulesFast(TransactionRequest request, List<String> violations) {
        if (request.getPayload() != null && request.getPayload().getMerchantId() == null) {
            violations.add("Merchant ID is mandatory for POS transactions");
        }
    }

    private void validateUpiRulesFast(TransactionRequest request, List<String> violations) {
        if (request.getPayload() != null &&
                request.getPayload().getAmount() != null &&
                request.getPayload().getAmount().compareTo(DEFAULT_UPI_MAX) > 0) {
            violations.add("UPI transaction amount exceeds limit");
        }
    }

    /**
     * Determine if system is in high-load mode (simplified validation)
     */
    private boolean isHighLoadMode() {
        // You can implement load detection logic here
        // For now, return false to use normal validation
        // During load testing, you could set this to true via a config flag
        return Boolean.parseBoolean(System.getProperty("high.load.mode", "false"));
    }

    /**
     * Async validation for high-throughput scenarios
     */
    public CompletableFuture<Void> validateBusinessRulesAsync(TransactionRequest request) {
        return CompletableFuture.runAsync(() -> validateBusinessRules(request));
    }

    /**
     * Cached channel rules data class
     */
    private static class ChannelRules {
        private final boolean active;
        private final BigDecimal minLimit;
        private final BigDecimal maxLimit;
        private final LocalTime businessStart;
        private final LocalTime businessEnd;

        public ChannelRules(boolean active, BigDecimal minLimit, BigDecimal maxLimit,
                            LocalTime businessStart, LocalTime businessEnd) {
            this.active = active;
            this.minLimit = minLimit;
            this.maxLimit = maxLimit;
            this.businessStart = businessStart;
            this.businessEnd = businessEnd;
        }

        public boolean isActive() { return active; }
        public BigDecimal getMinLimit() { return minLimit; }
        public BigDecimal getMaxLimit() { return maxLimit; }
        public LocalTime getBusinessStart() { return businessStart; }
        public LocalTime getBusinessEnd() { return businessEnd; }
    }
}