package com.concerto.omnichannel.service;

import com.concerto.omnichannel.entity.ClientCredentials;
import com.concerto.omnichannel.repository.ClientCredentialsRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
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

    // In-memory tracking for failed attempts (in production, use Redis)
    private final ConcurrentHashMap<String, FailedAttempt> failedAttempts = new ConcurrentHashMap<>();

    public AuthenticationService(
            ClientCredentialsRepository credentialsRepository,
            @Value("${auth.jwt.secret}") String jwtSecret,
            @Value("${auth.jwt.expirationMs}") long jwtExpirationMs) {
        this.credentialsRepository = credentialsRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
    }

    public boolean authenticate(String clientId, String clientSecret, String token, String channelId) {
        try {
            // Check if client is locked out
            if (isClientLockedOut(clientId)) {
                logger.warn("Authentication failed - Client {} is locked out", clientId);
                return false;
            }

            boolean authenticated = false;

            if (token != null && !token.isEmpty()) {
                authenticated = authenticateWithJwt(token, channelId);
            } else if (clientId != null && clientSecret != null) {
                authenticated = authenticateWithCredentials(clientId, clientSecret, channelId);
            }

            if (authenticated) {
                // Clear failed attempts on successful authentication
                failedAttempts.remove(clientId);
                logger.info("Authentication successful for client: {}", clientId);
            } else {
                // Track failed attempt
                trackFailedAttempt(clientId);
                logger.warn("Authentication failed for client: {}", clientId);
            }

            return authenticated;

        } catch (Exception e) {
            logger.error("Authentication error for client: {}", clientId, e);
            trackFailedAttempt(clientId);
            return false;
        }
    }

    @Cacheable(value = "clientCredentials", key = "#clientId")
    public boolean authenticateWithCredentials(String clientId, String clientSecret, String channelId) {
        Optional<ClientCredentials> credentialsOpt = credentialsRepository.findByClientIdAndChannelId(clientId, channelId);

        if (credentialsOpt.isEmpty()) {
            logger.warn("Client credentials not found: {}", clientId);
            return false;
        }

        ClientCredentials credentials = credentialsOpt.get();

        // Check if credentials are active and not expired
        if (!credentials.isActive() || credentials.getExpiryDate().isBefore(LocalDateTime.now())) {
            logger.warn("Client credentials inactive or expired: {}", clientId);
            return false;
        }

        return passwordEncoder.matches(clientSecret, credentials.getHashedSecret());
    }

    public boolean authenticateWithJwt(String token, String channelId) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(jwtSecretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Validate token expiration
            if (claims.getExpiration().before(new Date())) {
                logger.warn("JWT token expired");
                return false;
            }

            // Validate channel in token
            String tokenChannel = claims.get("channel", String.class);
            if (!channelId.equals(tokenChannel)) {
                logger.warn("JWT token channel mismatch. Expected: {}, Found: {}", channelId, tokenChannel);
                return false;
            }

            return claims.getSubject() != null;

        } catch (Exception e) {
            logger.warn("JWT authentication failed: {}", e.getMessage());
            return false;
        }
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
}