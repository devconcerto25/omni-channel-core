package com.concerto.omnichannel.service;

import com.concerto.omnichannel.entity.ClientCredentials;
import com.concerto.omnichannel.entity.MerchantInfo;
import com.concerto.omnichannel.repository.ClientCredentialsRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final long LOCKOUT_DURATION_MINUTES = 15;

    private final ClientCredentialsRepository credentialsRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecretKey jwtSecretKey;
    private final long jwtExpirationMs;

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // In-memory caches for performance
    private final ConcurrentHashMap<String, CachedCredentials> credentialsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FailedAttempt> failedAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedAuthResult> authResultsCache = new ConcurrentHashMap<>();

    // Cache validity periods
    private static final long CREDENTIALS_CACHE_TTL = 300000; // 5 minutes
    private static final long AUTH_RESULT_CACHE_TTL = 60000;  // 1 minute
    private static final long JWT_CACHE_TTL = 1800000;       // 30 minutes

    public AuthenticationService(
            ClientCredentialsRepository credentialsRepository,
            @Value("${auth.jwt.secret}") String jwtSecret,
            @Value("${auth.jwt.expirationMs}") long jwtExpirationMs) {
        this.credentialsRepository = credentialsRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(4); // Reduced rounds for performance
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * Main authentication method with multiple optimizations
     */
    public boolean authenticate(String clientId, String clientSecret, String token, String channelId, String correlationId) {
        try {
            // Quick lockout check
            if (isClientLockedOut(clientId)) {
                logger.debug("Client {} is locked out", clientId);
                return false;
            }

            // Check cached auth result first
            String cacheKey = generateAuthCacheKey(clientId, clientSecret, token, channelId);
            CachedAuthResult cachedResult = authResultsCache.get(cacheKey);
            if (cachedResult != null && !cachedResult.isExpired()) {
                logger.debug("Using cached auth result for client: {}", clientId);
                return cachedResult.isAuthenticated();
            }

            boolean authenticated = false;

            if (token != null && !token.isEmpty()) {
                authenticated = authenticateWithJwtCached(token, channelId);
            } else if (clientId != null && clientSecret != null) {
                authenticated = authenticateWithCredentialsCached(clientId, clientSecret, channelId);
            }

            if (authenticated) {
                // Parallel merchant validation for better performance
                CompletableFuture<Boolean> merchantValid = validateMerchantAsync(clientId, channelId);

                try {
                    boolean merchantValidation = merchantValid.get();
                    if (!merchantValidation) {
                        authenticated = false;
                        logger.debug("Merchant validation failed for client: {}", clientId);
                    }
                } catch (Exception e) {
                    logger.warn("Merchant validation error for client: {}", clientId, e);
                    authenticated = false;
                }

                if (authenticated) {
                    // Clear failed attempts on successful authentication
                    failedAttempts.remove(clientId);
                    logger.debug("Authentication successful for client: {}", clientId);
                }
            }

            if (!authenticated) {
                trackFailedAttempt(clientId);
                logger.debug("Authentication failed for client: {}", clientId);
            }

            // Cache the auth result
            authResultsCache.put(cacheKey, new CachedAuthResult(authenticated, System.currentTimeMillis()));

            return authenticated;

        } catch (Exception e) {
            logger.error("Authentication error for client: {}", clientId, e);
            trackFailedAttempt(clientId);
            return false;
        }
    }

    /**
     * Fast asynchronous authentication
     */
    public CompletableFuture<Boolean> authenticateAsync(String clientId, String clientSecret,
                                                        String token, String channel, String correlationId) {
        return CompletableFuture.supplyAsync(() ->
                authenticate(clientId, clientSecret, token, channel, correlationId));
    }

    /**
     * Cached merchant validation with async calls
     */
    private CompletableFuture<Boolean> validateMerchantAsync(String clientId, String channelId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Get merchant ID (cached)
                String merchantId = getMerchantIdFromClientCached(clientId, channelId);
                if (merchantId == null) {
                    return false;
                }

                // 2. Validate merchant exists and is active (cached)
                Optional<MerchantInfo> merchantInfo = merchantService.getMerchantInfoCached(merchantId);
                if (merchantInfo.isEmpty() || !merchantInfo.get().getActive()) {
                    return false;
                }

                // 3. Validate merchant has credentials for the channel (cached)
                return merchantService.getMerchantCredentialsCached(merchantId, channelId).isPresent();

            } catch (Exception e) {
                logger.warn("Merchant validation error", e);
                return false;
            }
        });
    }

    /**
     * Cached merchant ID lookup
     */
    private String getMerchantIdFromClientCached(String clientId, String channelId) {
        String cacheKey = "merchant:" + clientId + ":" + channelId;

        try {
            // Check Redis cache first
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return (String) cached;
            }

            // Fallback to database
            Optional<ClientCredentials> clientCreds = credentialsRepository.findByClientIdAndChannelId(clientId, channelId);
            String merchantId = clientCreds.map(ClientCredentials::getMerchantId).orElse(null);

            // Cache result
            if (merchantId != null) {
                redisTemplate.opsForValue().set(cacheKey, merchantId, Duration.ofMinutes(10));
            }

            return merchantId;

        } catch (Exception e) {
            logger.warn("Error getting cached merchant ID for client: {}", clientId, e);
            return null;
        }
    }

    /**
     * Ultra-fast credential authentication with multi-level caching
     */
    private boolean authenticateWithCredentialsCached(String clientId, String clientSecret, String channelId) {
        try {
            // Check in-memory cache first
            String cacheKey = clientId + ":" + channelId;
            CachedCredentials cached = credentialsCache.get(cacheKey);

            if (cached != null && !cached.isExpired()) {
                logger.debug("Using cached credentials for client: {}", clientId);
                return passwordEncoder.matches(clientSecret, cached.getHashedSecret());
            }

            // Load from database if not cached
            Optional<ClientCredentials> credentialsOpt = credentialsRepository.findByClientIdAndChannelIdAndActiveTrue(clientId, channelId);

            if (credentialsOpt.isEmpty()) {
                logger.debug("Client credentials not found: {} for channel: {}", clientId, channelId);
                return false;
            }

            ClientCredentials credentials = credentialsOpt.get();

            // Check if credentials are active and not expired
            if (!credentials.isActive() || credentials.getExpiryDate().isBefore(LocalDateTime.now())) {
                logger.debug("Client credentials inactive or expired: {}", clientId);
                return false;
            }

            // Cache the credentials for future use
            credentialsCache.put(cacheKey, new CachedCredentials(
                    credentials.getHashedSecret(),
                    credentials.isActive(),
                    credentials.getExpiryDate(),
                    System.currentTimeMillis()
            ));

            // Verify password
            boolean matches = passwordEncoder.matches(clientSecret, credentials.getHashedSecret());
            logger.debug("Password verification result for client {}: {}", clientId, matches);

            return matches;

        } catch (Exception e) {
            logger.warn("Error in cached credential authentication for client: {}", clientId, e);
            return false;
        }
    }

    /**
     * JWT authentication with caching
     */
    private boolean authenticateWithJwtCached(String token, String channelId) {
        try {
            // Check JWT cache first
            String jwtCacheKey = "jwt:" + token.hashCode();
            Object cachedResult = redisTemplate.opsForValue().get(jwtCacheKey);

            if (cachedResult != null) {
                logger.debug("Using cached JWT validation result");
                return (Boolean) cachedResult;
            }

            // Parse and validate JWT
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Validate token expiration
            if (claims.getExpiration().before(new Date())) {
                logger.debug("JWT token expired");
                return false;
            }

            // Validate channel in token
            String tokenChannel = claims.get("channel", String.class);
            if (!channelId.equals(tokenChannel)) {
                logger.debug("JWT token channel mismatch. Expected: {}, Found: {}", channelId, tokenChannel);
                return false;
            }

            boolean isValid = claims.getSubject() != null;

            // Cache the result (only cache valid tokens)
            if (isValid) {
                long ttl = Math.min(JWT_CACHE_TTL, claims.getExpiration().getTime() - System.currentTimeMillis());
                if (ttl > 0) {
                    redisTemplate.opsForValue().set(jwtCacheKey, true, Duration.ofMillis(ttl));
                }
            }

            return isValid;

        } catch (Exception e) {
            logger.debug("JWT authentication failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Legacy method maintained for backward compatibility
     */
    @Cacheable(value = "clientCredentials", key = "#clientId + ':' + #channelId")
    public boolean authenticateWithCredentials(String clientId, String clientSecret, String channelId) {
        return authenticateWithCredentialsCached(clientId, clientSecret, channelId);
    }

    public boolean authenticateWithJwt(String token, String channelId) {
        return authenticateWithJwtCached(token, channelId);
    }

    public String generateJwtToken(String clientId, String channelId) {
        Date expiryDate = new Date(System.currentTimeMillis() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject(clientId)
                .claim("channel", channelId)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(jwtSecretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    private void trackFailedAttempt(String clientId) {
        FailedAttempt attempt = failedAttempts.computeIfAbsent(clientId,
                k -> new FailedAttempt(0, LocalDateTime.now()));

        attempt.incrementAttempts();
        attempt.setLastAttempt(LocalDateTime.now());
    }

    private boolean isClientLockedOut(String clientId) {
        FailedAttempt attempt = failedAttempts.get(clientId);
        if (attempt == null) return false;

        if (attempt.getAttempts() >= MAX_FAILED_ATTEMPTS) {
            LocalDateTime lockoutExpiry = attempt.getLastAttempt().plusMinutes(LOCKOUT_DURATION_MINUTES);
            if (LocalDateTime.now().isBefore(lockoutExpiry)) {
                return true;
            } else {
                // Lockout expired, reset attempts
                failedAttempts.remove(clientId);
            }
        }
        return false;
    }

    private String generateAuthCacheKey(String clientId, String clientSecret, String token, String channelId) {
        return String.format("auth:%s:%s:%s:%s",
                clientId != null ? clientId : "null",
                clientSecret != null ? Integer.toString(clientSecret.hashCode()) : "null",
                token != null ? Integer.toString(token.hashCode()) : "null",
                channelId);
    }

    /**
     * Cache cleanup scheduled task
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupCaches() {
        long currentTime = System.currentTimeMillis();

        // Cleanup credentials cache
        credentialsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Cleanup auth results cache
        authResultsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

        logger.debug("Cache cleanup completed. Credentials cache size: {}, Auth results cache size: {}",
                credentialsCache.size(), authResultsCache.size());
    }

    // Inner classes for caching
    private static class FailedAttempt {
        private int attempts;
        private LocalDateTime lastAttempt;

        public FailedAttempt(int attempts, LocalDateTime lastAttempt) {
            this.attempts = attempts;
            this.lastAttempt = lastAttempt;
        }

        public void incrementAttempts() { this.attempts++; }
        public int getAttempts() { return attempts; }
        public LocalDateTime getLastAttempt() { return lastAttempt; }
        public void setLastAttempt(LocalDateTime lastAttempt) { this.lastAttempt = lastAttempt; }
    }

    private static class CachedCredentials {
        private final String hashedSecret;
        private final boolean active;
        private final LocalDateTime expiryDate;
        private final long cacheTime;

        public CachedCredentials(String hashedSecret, boolean active, LocalDateTime expiryDate, long cacheTime) {
            this.hashedSecret = hashedSecret;
            this.active = active;
            this.expiryDate = expiryDate;
            this.cacheTime = cacheTime;
        }

        public String getHashedSecret() { return hashedSecret; }
        public boolean isActive() { return active; }
        public LocalDateTime getExpiryDate() { return expiryDate; }

        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > CREDENTIALS_CACHE_TTL ||
                    expiryDate.isBefore(LocalDateTime.now()) ||
                    !active;
        }
    }

    private static class CachedAuthResult {
        private final boolean authenticated;
        private final long cacheTime;

        public CachedAuthResult(boolean authenticated, long cacheTime) {
            this.authenticated = authenticated;
            this.cacheTime = cacheTime;
        }

        public boolean isAuthenticated() { return authenticated; }

        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > AUTH_RESULT_CACHE_TTL;
        }
    }
}