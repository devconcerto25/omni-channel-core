package com.concerto.omnichannel.service;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.exceptions.CorrelationException;
import com.concerto.omnichannel.exceptions.STANGenerationException;
import com.concerto.omnichannel.model.STANStatistics;
import com.concerto.omnichannel.model.RequestContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Production-grade STAN Generation and Correlation System
 * Implements ISO8583 compliant sequential STAN generation per terminal
 */
@Service
public class STANGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(STANGenerationService.class);

    // STAN constraints
    private static final int STAN_MIN = 1;
    private static final int STAN_MAX = 999999;
    private static final String STAN_FORMAT = "%06d";

    // Redis key patterns
    private static final String STAN_COUNTER_KEY = "STAN_COUNTER:%s:%s";
    private static final String PENDING_REQUEST_KEY = "PENDING_REQ:%s:%s:%s";
    private static final String REQUEST_CONTEXT_KEY = "REQ_CTX:%s:%s:%s";

    // TTL settings
    private static final long PENDING_REQUEST_TTL = 300; // 5 minutes
    private static final long CONTEXT_TTL = 600; // 10 minutes

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Local cache for CompletableFutures (not stored in Redis)
    private final ConcurrentHashMap<String, CompletableFuture<TransactionResponse>> pendingFutures =
            new ConcurrentHashMap<>();

    /*Lua script for atomic STAN increment with wrap-around*/
    private static final String STAN_INCREMENT_SCRIPT =
            "local current = redis.call('GET', KEYS[1])\n" +
                    "if current == false then\n" +
                    "    current = 0\n" +
                    "else\n" +
                    "    current = tonumber(current)\n" +
                    "end\n" +
                    "current = current + 1\n" +
                    "if current > 999999 then\n" +
                    "    current = 1\n" +
                    "end\n" +
                    "redis.call('SET', KEYS[1], current)\n" +
                    "return current";

    private final DefaultRedisScript<Long> stanIncrementScript;

    public STANGenerationService() {
        stanIncrementScript = new DefaultRedisScript<>();
        stanIncrementScript.setScriptText(STAN_INCREMENT_SCRIPT);
        stanIncrementScript.setResultType(Long.class);
    }

    /* Generate next sequential STAN for a specific terminal*/
    public String generateSTAN(String merchantId, String terminalId) {
        try {
            String counterKey = String.format(STAN_COUNTER_KEY, merchantId, terminalId);

            // Atomic increment with wrap-around using Lua script
            Long stanNumber = redisTemplate.execute(stanIncrementScript,
                    Collections.singletonList(counterKey));

            String stan = String.format(STAN_FORMAT, stanNumber);

            logger.debug("Generated STAN {} for merchant {} terminal {}",
                    stan, merchantId, terminalId);

            return stan;

        } catch (Exception e) {
            logger.error("Failed to generate STAN for merchant {} terminal {}: {}",
                    merchantId, terminalId, e.getMessage(), e);
            throw new STANGenerationException("STAN generation failed", e);
        }
    }

    /*Initialize STAN counter for a new terminal*/
    public void initializeTerminalSTAN(String merchantId, String terminalId, int startingSTAN) {
        if (startingSTAN < STAN_MIN || startingSTAN > STAN_MAX) {
            throw new IllegalArgumentException("Invalid starting STAN: " + startingSTAN);
        }

        String counterKey = String.format(STAN_COUNTER_KEY, merchantId, terminalId);

        // Only set if key doesn't exist
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(counterKey, startingSTAN - 1);

        if (Boolean.TRUE.equals(wasSet)) {
            logger.info("Initialized STAN counter for merchant {} terminal {} starting at {}",
                    merchantId, terminalId, startingSTAN);
        } else {
            logger.debug("STAN counter already exists for merchant {} terminal {}",
                    merchantId, terminalId);
        }
    }

    /*Get current STAN counter value (for monitoring/debugging)*/
    public int getCurrentSTAN(String merchantId, String terminalId) {
        String counterKey = String.format(STAN_COUNTER_KEY, merchantId, terminalId);
        Object value = redisTemplate.opsForValue().get(counterKey);
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /*Store pending request for correlation*/
    public void storePendingRequest(String merchantId, String terminalId, String stan,
                                    TransactionRequest request, CompletableFuture<TransactionResponse> future) {

        String pendingKey = String.format(PENDING_REQUEST_KEY, merchantId, terminalId, stan);
        String contextKey = String.format(REQUEST_CONTEXT_KEY, merchantId, terminalId, stan);
        String futureKey = merchantId + ":" + terminalId + ":" + stan;

        try {
            // Store request context in Redis
            RequestContext context = new RequestContext(
                    merchantId, terminalId, stan, request, LocalDateTime.now()
            );

            redisTemplate.opsForValue().set(contextKey, context, CONTEXT_TTL, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(pendingKey, "PENDING", PENDING_REQUEST_TTL, TimeUnit.SECONDS);

            // Store future locally (cannot serialize CompletableFuture to Redis)
            pendingFutures.put(futureKey, future);

            logger.debug("Stored pending request for merchant {} terminal {} STAN {}",
                    merchantId, terminalId, stan);

        } catch (Exception e) {
            logger.error("Failed to store pending request: {}", e.getMessage(), e);
            throw new CorrelationException("Failed to store pending request", e);
        }
    }

    /*Correlate response with pending request*/
    public boolean correlateResponse(TransactionResponse response) {
        JsonObject responsePayload = convertToJson(response.getPayload());
        String stan = String.valueOf(responsePayload.get("stan"));

        // Search across all merchant-terminal combinations for this STAN
        List<String> matchingKeys = findPendingRequestsBySTAN(stan);

        if (matchingKeys.isEmpty()) {
            logger.warn("No pending request found for STAN {}", stan);
            return false;
        }

        // If multiple matches, use additional fields for disambiguation
        RequestContext matchedContext = disambiguateRequests(matchingKeys, response);

        if (matchedContext == null) {
            logger.warn("Could not disambiguate response for STAN {}", stan);
            return false;
        }

        return completeRequest(matchedContext, response);
    }

    /*Find all pending requests matching a STAN across all terminals*/
    private List<String> findPendingRequestsBySTAN(String stan) {
        String pattern = String.format("PENDING_REQ:*:*:%s", stan);
        return redisTemplate.keys(pattern).stream()
                .filter(key -> {
                    Object value = redisTemplate.opsForValue().get(key);
                    return "PENDING".equals(value);
                })
                .map(key -> key.replace("PENDING_REQ:", "REQ_CTX:"))
                .toList();
    }

    /*Disambiguate multiple matching requests using RRN, amount, timestamp*/
    private RequestContext disambiguateRequests(List<String> contextKeys, TransactionResponse response) {
        RequestContext bestMatch = null;
        int matchScore = 0;

        for (String contextKey : contextKeys) {
            RequestContext context = (RequestContext) redisTemplate.opsForValue().get(contextKey);
            if (context == null) continue;

            int score = calculateMatchScore(context, response);
            if (score > matchScore) {
                matchScore = score;
                bestMatch = context;
            }
        }

        return bestMatch;
    }

    /*Calculate match score for disambiguation*/
    private int calculateMatchScore(RequestContext context, TransactionResponse response) {
        int score = 0;

        // Exact STAN match (already confirmed)
        score += 10;

        JsonObject responsePayload = convertToJson(response.getPayload());
        // RRN match
        if (context.getRequest().getPayload().getAdditionalFields().get("rrn") != null &&
                context.getRequest().getPayload().getAdditionalFields().get("rrn").equals(String.valueOf(responsePayload.get("rrn")))) {
            score += 50;
        }

        // Amount match
        if (context.getRequest().getPayload().getAmount() != null &&
                context.getRequest().getPayload().getAmount().equals(BigDecimal.valueOf(Long.parseLong(String.valueOf(responsePayload.get("amount")))))) {
            score += 30;
        }

        // Merchant/Terminal match
        if (context.getMerchantId().equals(String.valueOf(responsePayload.get("merchantId"))) &&
                context.getTerminalId().equals(String.valueOf(responsePayload.get("terminalId")))) {
            score += 20;
        }

        // Timestamp proximity (within reasonable time window)
        long timeDiff = java.time.Duration.between(context.getTimestamp(), LocalDateTime.now()).toSeconds();
        if (timeDiff < 60) { // Within 1 minute
            score += 15;
        } else if (timeDiff < 300) { // Within 5 minutes
            score += 5;
        }

        return score;
    }

    /*Complete the pending request with response*/
    private boolean completeRequest(RequestContext context, TransactionResponse response) {
        String futureKey = context.getMerchantId() + ":" + context.getTerminalId() + ":" + context.getStan();
        String pendingKey = String.format(PENDING_REQUEST_KEY,
                context.getMerchantId(), context.getTerminalId(), context.getStan());
        String contextKey = String.format(REQUEST_CONTEXT_KEY,
                context.getMerchantId(), context.getTerminalId(), context.getStan());

        try {
            // Get the waiting future
            CompletableFuture<TransactionResponse> future = pendingFutures.remove(futureKey);

            if (future != null && !future.isDone()) {
                future.complete(response);
                logger.debug("Completed request for merchant {} terminal {} STAN {}",
                        context.getMerchantId(), context.getTerminalId(), context.getStan());
            }

            // Cleanup Redis entries
            redisTemplate.delete(pendingKey);
            redisTemplate.delete(contextKey);

            return true;

        } catch (Exception e) {
            logger.error("Failed to complete request: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cleanup expired pending requests
     */
    public void cleanupExpiredRequests() {
        try {
            String pattern = "PENDING_REQ:*:*:*";
            redisTemplate.keys(pattern).forEach(key -> {
                Object value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    // Key expired, cleanup corresponding future
                    String futureKey = key.replace("PENDING_REQ:", "").replace(":", ":");
                    CompletableFuture<TransactionResponse> future = pendingFutures.remove(futureKey);
                    if (future != null && !future.isDone()) {
                        future.completeExceptionally(new TimeoutException("Request timeout"));
                    }
                }
            });

            logger.debug("Cleanup completed. Active futures: {}", pendingFutures.size());

        } catch (Exception e) {
            logger.error("Failed to cleanup expired requests: {}", e.getMessage(), e);
        }
    }

    /**
     * Get statistics for monitoring
     */
    public STANStatistics getStatistics() {
        int activeFutures = pendingFutures.size();
        int totalTerminals = redisTemplate.keys("STAN_COUNTER:*").size();
        int pendingRequests = redisTemplate.keys("PENDING_REQ:*").size();

        return new STANStatistics(activeFutures, totalTerminals, pendingRequests);
    }

    public JsonObject convertToJson(String payload) {
        return JsonParser.parseString(payload).getAsJsonObject();
    }
}


