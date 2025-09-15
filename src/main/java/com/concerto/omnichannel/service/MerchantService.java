package com.concerto.omnichannel.service;

import com.concerto.omnichannel.entity.*;
import com.concerto.omnichannel.repository.*;
import com.concerto.omnichannel.dto.TransactionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MerchantService {

    private static final Logger logger = LoggerFactory.getLogger(MerchantService.class);

    @Autowired
    private MerchantInfoRepository merchantInfoRepository;

    @Autowired
    private MerchantCredentialsRepository merchantCredentialsRepository;

    @Autowired
    private MerchantTerminalRepository merchantTerminalRepository;

    @Autowired
    private MerchantChannelConfigRepository merchantChannelConfigRepository;


    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // In-memory cache for frequently accessed merchant data
    private final ConcurrentHashMap<String, CachedMerchantInfo> merchantInfoCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedMerchantCredentials> merchantCredentialsCache = new ConcurrentHashMap<>();

    private static final long MERCHANT_INFO_CACHE_TTL = 600000; // 10 minutes
    private static final long MERCHANT_CREDENTIALS_CACHE_TTL = 300000; // 5 minutes


    /**
     * Get merchant information by merchant ID
     */
    @Cacheable(value = "merchantInfo", key = "#merchantId")
    public Optional<MerchantInfo> getMerchantInfo(String merchantId) {
        logger.debug("Getting merchant info for merchantId: {}", merchantId);
        return merchantInfoRepository.findByMerchantId(merchantId);
    }

    /**
     * Get merchant credentials for specific channel
     */
    @Cacheable(value = "merchantCredentials", key = "#merchantId + '_' + #channel")
    public Optional<MerchantCredentials> getMerchantCredentials(String merchantId, String channel) {
        logger.debug("Getting merchant credentials for merchantId: {}, channel: {}", merchantId, channel);
        return merchantCredentialsRepository.findActiveMerchantCredentials(merchantId, channel);
    }

    public Optional<MerchantCredentials> getMerchantCredentialsCached(String merchantId, String channelId) {
        try {
            String cacheKey = merchantId + ":" + channelId;

            // Check in-memory cache first
            CachedMerchantCredentials cached = merchantCredentialsCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Using cached merchant credentials for: {} channel: {}", merchantId, channelId);
                return Optional.of(cached.getMerchantCredentials());
            }

            // Check Redis cache
            String redisKey = "merchant:credentials:" + cacheKey;
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            if (redisValue != null) {
                MerchantCredentials credentials = (MerchantCredentials) redisValue;
                // Update in-memory cache
                merchantCredentialsCache.put(cacheKey, new CachedMerchantCredentials(credentials, System.currentTimeMillis()));
                logger.debug("Using Redis cached merchant credentials for: {} channel: {}", merchantId, channelId);
                return Optional.of(credentials);
            }

            // Fallback to database
            Optional<MerchantCredentials> credentialsOpt =
                    merchantCredentialsRepository.findByMerchantIdAndChannelAndIsActiveTrue(merchantId, channelId);

            if (credentialsOpt.isPresent()) {
                MerchantCredentials credentials = credentialsOpt.get();

                // Check if credentials are active (adjust field name as needed)
                if (!isMerchantCredentialsActive(credentials)) {
                    logger.debug("Merchant credentials are inactive for: {} channel: {}", merchantId, channelId);
                    return Optional.empty();
                }

                // Cache in both memory and Redis
                merchantCredentialsCache.put(cacheKey, new CachedMerchantCredentials(credentials, System.currentTimeMillis()));
                redisTemplate.opsForValue().set(redisKey, credentials, Duration.ofMillis(MERCHANT_CREDENTIALS_CACHE_TTL));

                logger.debug("Loaded and cached merchant credentials for: {} channel: {}", merchantId, channelId);
                return Optional.of(credentials);
            } else {
                logger.debug("Merchant credentials not found for: {} channel: {}", merchantId, channelId);
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Error retrieving cached merchant credentials for: {} channel: {}", merchantId, channelId, e);
            return Optional.empty();
        }
    }

    public Optional<MerchantInfo> getMerchantInfoCached(String merchantId) {
        try {
            // Check in-memory cache first
            CachedMerchantInfo cached = merchantInfoCache.get(merchantId);
            if (cached != null && !cached.isExpired()) {
                logger.debug("Using cached merchant info for: {}", merchantId);
                return Optional.of(cached.getMerchantInfo());
            }

            // Check Redis cache
            String redisKey = "merchant:info:" + merchantId;
            Object redisValue = redisTemplate.opsForValue().get(redisKey);
            if (redisValue != null) {
                MerchantInfo merchantInfo = (MerchantInfo) redisValue;
                // Update in-memory cache
                merchantInfoCache.put(merchantId, new CachedMerchantInfo(merchantInfo, System.currentTimeMillis()));
                logger.debug("Using Redis cached merchant info for: {}", merchantId);
                return Optional.of(merchantInfo);
            }

            // Fallback to database - use the correct repository method
            Optional<MerchantInfo> merchantInfoOpt = merchantInfoRepository.findByMerchantId(merchantId);

            if (merchantInfoOpt.isPresent()) {
                MerchantInfo merchantInfo = merchantInfoOpt.get();

                // Check if merchant is active (adjust field name as needed)
                if (!isMerchantActive(merchantInfo)) {
                    logger.debug("Merchant is inactive: {}", merchantId);
                    return Optional.empty();
                }

                // Cache in both memory and Redis
                merchantInfoCache.put(merchantId, new CachedMerchantInfo(merchantInfo, System.currentTimeMillis()));
                redisTemplate.opsForValue().set(redisKey, merchantInfo, Duration.ofMillis(MERCHANT_INFO_CACHE_TTL));

                logger.debug("Loaded and cached merchant info for: {}", merchantId);
                return Optional.of(merchantInfo);
            } else {
                logger.debug("Merchant info not found for: {}", merchantId);
                return Optional.empty();
            }

        } catch (Exception e) {
            logger.error("Error retrieving cached merchant info for: {}", merchantId, e);
            return Optional.empty();
        }
    }

    private boolean isMerchantActive(MerchantInfo merchantInfo) {
        try {
            // Option 1: If you have an 'active' field with different name
            // return merchantInfo.getIsActive(); // or getStatus(), getEnabled(), etc.

            // Option 2: If you have a status field
            // return "ACTIVE".equals(merchantInfo.getStatus());

            // Option 3: If you check other fields for active status
            // return merchantInfo.getValidUntil().isAfter(LocalDateTime.now());

            // For now, assume all merchants are active if found
            // You should replace this with your actual logic
            return merchantInfo != null;

        } catch (Exception e) {
            logger.warn("Error checking merchant active status for: {}", merchantInfo, e);
            return false;
        }
    }

    /**
     * Helper method to check if merchant credentials are active
     * Adjust this method based on your actual MerchantCredentials entity structure
     */
    private boolean isMerchantCredentialsActive(MerchantCredentials credentials) {
        try {
            // Option 1: If you have an 'active' field with different name
            // return credentials.getIsActive(); // or getStatus(), getEnabled(), etc.

            // Option 2: If you have a status field
            // return "ACTIVE".equals(credentials.getStatus());

            // Option 3: If you check expiry dates
            // return credentials.getExpiryDate().isAfter(LocalDateTime.now());

            // For now, assume all credentials are active if found
            // You should replace this with your actual logic
            return credentials != null;

        } catch (Exception e) {
            logger.warn("Error checking credentials active status for: {}", credentials, e);
            return false;
        }
    }

    public boolean isValidMerchantChannel(String merchantId, String channelId) {
        try {
            Optional<MerchantInfo> merchantInfo = getMerchantInfoCached(merchantId);
            if (merchantInfo.isEmpty() || !merchantInfo.get().getActive()) {
                return false;
            }

            Optional<MerchantCredentials> credentials = getMerchantCredentialsCached(merchantId, channelId);
            return credentials.isPresent() && credentials.get().getActive();

        } catch (Exception e) {
            logger.error("Error validating merchant channel: {} {}", merchantId, channelId, e);
            return false;
        }
    }

    /**
     * Get merchant channel configuration
     */
    @Cacheable(value = "merchantChannelConfig", key = "#merchantId + '_' + #channel")
    public Optional<MerchantChannelConfig> getMerchantChannelConfig(String merchantId, String channel) {
        logger.debug("Getting merchant channel config for merchantId: {}, channel: {}", merchantId, channel);
        return merchantChannelConfigRepository.findEnabledChannelConfig(merchantId, channel);
    }

    /**
     * Get merchant terminal information
     */
    @Cacheable(value = "merchantTerminal", key = "#merchantId + '_' + #terminalId")
    public Optional<MerchantTerminal> getMerchantTerminal(String merchantId, String terminalId) {
        logger.debug("Getting merchant terminal for merchantId: {}, terminalId: {}", merchantId, terminalId);
        return merchantTerminalRepository.findByMerchantIdAndTerminalId(merchantId, terminalId);
    }

    /**
     * Validate if merchant can perform transaction on specific channel
     */
    public boolean validateMerchantChannelAccess(String merchantId, String channel, TransactionRequest request) {
        logger.debug("Validating merchant channel access for merchantId: {}, channel: {}", merchantId, channel);

        try {
            // 1. Check if merchant exists and is active
            Optional<MerchantInfo> merchantInfo = getMerchantInfo(merchantId);
            if (merchantInfo.isEmpty() || !merchantInfo.get().getActive()) {
                logger.warn("Merchant {} is not active or doesn't exist", merchantId);
                return false;
            }

            // 2. Check if merchant has valid credentials for the channel
            Optional<MerchantCredentials> credentials = getMerchantCredentials(merchantId, channel);
            if (credentials.isEmpty()) {
                logger.warn("No credentials found for merchant {} on channel {}", merchantId, channel);
                return false;
            }

            // 3. Check channel configuration
            Optional<MerchantChannelConfig> channelConfig = getMerchantChannelConfig(merchantId, channel);
            if (channelConfig.isEmpty() || !channelConfig.get().getEnabled()) {
                logger.warn("Channel {} is not enabled for merchant {}", channel, merchantId);
                return false;
            }

            // 4. Validate transaction limits
            if (request.getPayload() != null && request.getPayload().getAmount() != null) {
                MerchantChannelConfig config = channelConfig.get();
                if (request.getPayload().getAmount().compareTo(config.getMinAmount()) < 0) {
                    logger.warn("Transaction amount {} below minimum {} for merchant {}",
                            request.getPayload().getAmount(), config.getMinAmount(), merchantId);
                    return false;
                }
                if (request.getPayload().getAmount().compareTo(config.getMaxAmount()) > 0) {
                    logger.warn("Transaction amount {} exceeds maximum {} for merchant {}",
                            request.getPayload().getAmount(), config.getMaxAmount(), merchantId);
                    return false;
                }
            }

            // 5. For POS transactions, validate terminal
            if ("POS".equalsIgnoreCase(channel) && request.getPayload() != null
                    && request.getPayload().getTerminalId() != null) {
                Optional<MerchantTerminal> terminal = getMerchantTerminal(merchantId, request.getPayload().getTerminalId());
                if (terminal.isEmpty() || !terminal.get().getActive()) {
                    logger.warn("Terminal {} is not active for merchant {}",
                            request.getPayload().getTerminalId(), merchantId);
                    return false;
                }
            }

            logger.info("Merchant channel access validation successful for merchant: {}, channel: {}",
                    merchantId, channel);
            return true;

        } catch (Exception e) {
            logger.error("Error validating merchant channel access for merchant: {}, channel: {}",
                    merchantId, channel, e);
            return false;
        }
    }

    /**
     * Get all enabled channels for a merchant
     */
    public List<MerchantChannelConfig> getEnabledChannels(String merchantId) {
        logger.debug("Getting enabled channels for merchant: {}", merchantId);
        return merchantChannelConfigRepository.findByMerchantIdAndIsEnabledTrue(merchantId);
    }

    /**
     * Get all active terminals for a merchant
     */
    public List<MerchantTerminal> getActiveTerminals(String merchantId) {
        logger.debug("Getting active terminals for merchant: {}", merchantId);
        return merchantTerminalRepository.findByMerchantIdAndIsActiveTrue(merchantId);
    }

    /**
     * Create or update merchant information
     */
    @Transactional
    public MerchantInfo saveMerchantInfo(MerchantInfo merchantInfo) {
        logger.info("Saving merchant info for merchant: {}", merchantInfo.getMerchantId());
        return merchantInfoRepository.save(merchantInfo);
    }

    /**
     * Create or update merchant credentials
     */
    @Transactional
    public MerchantCredentials saveMerchantCredentials(MerchantCredentials credentials) {
        logger.info("Saving merchant credentials for merchant: {}, channel: {}",
                credentials.getMerchantId(), credentials.getChannel());

        // Encrypt sensitive data before saving (implement encryption logic)
        encryptSensitiveData(credentials);

        return merchantCredentialsRepository.save(credentials);
    }

    /**
     * Create or update merchant channel configuration
     */
    @Transactional
    public MerchantChannelConfig saveMerchantChannelConfig(MerchantChannelConfig config) {
        logger.info("Saving merchant channel config for merchant: {}, channel: {}",
                config.getMerchantId(), config.getChannel());
        return merchantChannelConfigRepository.save(config);
    }

    /**
     * Create or update merchant terminal
     */
    @Transactional
    public MerchantTerminal saveMerchantTerminal(MerchantTerminal terminal) {
        logger.info("Saving merchant terminal for merchant: {}, terminal: {}",
                terminal.getMerchantId(), terminal.getTerminalId());
        return merchantTerminalRepository.save(terminal);
    }

    /**
     * Deactivate merchant
     */
    @Transactional
    public void deactivateMerchant(String merchantId) {
        logger.info("Deactivating merchant: {}", merchantId);
        Optional<MerchantInfo> merchantInfo = merchantInfoRepository.findByMerchantId(merchantId);
        if (merchantInfo.isPresent()) {
            MerchantInfo merchant = merchantInfo.get();
            merchant.setActive(false);
            merchant.setStatus("INACTIVE");
            merchantInfoRepository.save(merchant);
        }
    }

    /**
     * Get merchant credentials with decryption (for internal use)
     */
    public MerchantCredentials getDecryptedCredentials(String merchantId, String channel) {
        Optional<MerchantCredentials> credentials = getMerchantCredentials(merchantId, channel);
        if (credentials.isPresent()) {
            MerchantCredentials decrypted = credentials.get();
            // Implement decryption logic here
            decryptSensitiveData(decrypted);
            return decrypted;
        }
        return null;
    }

    /**
     * Check if merchant exists
     */
    public boolean merchantExists(String merchantId) {
        return merchantInfoRepository.existsByMerchantId(merchantId);
    }

    /**
     * Get merchant by client credentials
     */
    public String getMerchantIdByClientCredentials(String clientId) {
        // This would typically query client_credentials table
        // For now, returning a placeholder - implement based on your client_credentials table structure
        logger.debug("Getting merchant ID for client: {}", clientId);

        // Example implementation:
        // Optional<ClientCredentials> clientCreds = clientCredentialsRepository.findByClientId(clientId);
        // return clientCreds.map(ClientCredentials::getMerchantId).orElse(null);

        return null; // Implement based on your client_credentials structure
    }

    /**
     * Encrypt sensitive credential data (implement your encryption logic)
     */
    private void encryptSensitiveData(MerchantCredentials credentials) {
        // TODO: Implement encryption for sensitive fields
        // credentials.setApiSecret(encryptionService.encrypt(credentials.getApiSecret()));
        // credentials.setPassword(encryptionService.encrypt(credentials.getPassword()));
        logger.debug("Encrypting sensitive data for merchant: {}", credentials.getMerchantId());
    }

    /**
     * Decrypt sensitive credential data (implement your decryption logic)
     */
    private void decryptSensitiveData(MerchantCredentials credentials) {
        // TODO: Implement decryption for sensitive fields
        // credentials.setApiSecret(encryptionService.decrypt(credentials.getApiSecret()));
        // credentials.setPassword(encryptionService.decrypt(credentials.getPassword()));
        logger.debug("Decrypting sensitive data for merchant: {}", credentials.getMerchantId());
    }

    /**
     * Validate merchant transaction limits
     */
    public boolean validateTransactionLimits(String merchantId, String channel,
                                             java.math.BigDecimal amount, String terminalId) {
        try {
            // Check merchant level limits
            Optional<MerchantInfo> merchantInfo = getMerchantInfo(merchantId);
            if (merchantInfo.isPresent() && merchantInfo.get().getPerTransactionLimit() != null) {
                if (amount.compareTo(merchantInfo.get().getPerTransactionLimit()) > 0) {
                    logger.warn("Amount {} exceeds merchant per-transaction limit {} for merchant {}",
                            amount, merchantInfo.get().getPerTransactionLimit(), merchantId);
                    return false;
                }
            }

            // Check channel level limits
            Optional<MerchantChannelConfig> channelConfig = getMerchantChannelConfig(merchantId, channel);
            if (channelConfig.isPresent()) {
                MerchantChannelConfig config = channelConfig.get();
                if (amount.compareTo(config.getMinAmount()) < 0 ||
                        amount.compareTo(config.getMaxAmount()) > 0) {
                    logger.warn("Amount {} outside channel limits [{}, {}] for merchant {}",
                            amount, config.getMinAmount(), config.getMaxAmount(), merchantId);
                    return false;
                }
            }

            // Check terminal level limits for POS transactions
            if ("POS".equalsIgnoreCase(channel) && terminalId != null) {
                Optional<MerchantTerminal> terminal = getMerchantTerminal(merchantId, terminalId);
                if (terminal.isPresent() && terminal.get().getPerTransactionLimit() != null) {
                    if (amount.compareTo(terminal.get().getPerTransactionLimit()) > 0) {
                        logger.warn("Amount {} exceeds terminal limit {} for terminal {}",
                                amount, terminal.get().getPerTransactionLimit(), terminalId);
                        return false;
                    }
                }
            }

            return true;

        } catch (Exception e) {
            logger.error("Error validating transaction limits for merchant: {}", merchantId, e);
            return false;
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupCaches() {
        long currentTime = System.currentTimeMillis();

        // Cleanup merchant info cache
        merchantInfoCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Cleanup merchant credentials cache
        merchantCredentialsCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

        logger.debug("Merchant cache cleanup completed. Info cache size: {}, Credentials cache size: {}",
                merchantInfoCache.size(), merchantCredentialsCache.size());
    }

    /**
     * Manual cache invalidation for specific merchant
     */
    public void invalidateMerchantCache(String merchantId) {
        // Remove from in-memory cache
        merchantInfoCache.remove(merchantId);

        // Remove credentials for all channels
        merchantCredentialsCache.entrySet().removeIf(entry -> entry.getKey().startsWith(merchantId + ":"));

        // Remove from Redis
        redisTemplate.delete("merchant:info:" + merchantId);

        // Pattern-based deletion for credentials (this might be expensive, use cautiously)
        redisTemplate.keys("merchant:credentials:" + merchantId + ":*").forEach(key ->
                redisTemplate.delete(key));

        logger.info("Invalidated cache for merchant: {}", merchantId);
    }

    /**
     * Warm up cache for frequently used merchants
     */
    public void warmUpCache(String merchantId, String... channels) {
        try {
            // Warm up merchant info
            getMerchantInfoCached(merchantId);

            // Warm up credentials for specified channels
            for (String channel : channels) {
                getMerchantCredentialsCached(merchantId, channel);
            }

            logger.debug("Cache warmed up for merchant: {} channels: {}", merchantId, String.join(",", channels));

        } catch (Exception e) {
            logger.error("Error warming up cache for merchant: {}", merchantId, e);
        }
    }

    // Inner classes for caching
    private static class CachedMerchantInfo {
        private final MerchantInfo merchantInfo;
        private final long cacheTime;

        public CachedMerchantInfo(MerchantInfo merchantInfo, long cacheTime) {
            this.merchantInfo = merchantInfo;
            this.cacheTime = cacheTime;
        }

        public MerchantInfo getMerchantInfo() {
            return merchantInfo;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > MERCHANT_INFO_CACHE_TTL ||
                    !merchantInfo.getActive();
        }
    }

    private static class CachedMerchantCredentials {
        private final MerchantCredentials merchantCredentials;
        private final long cacheTime;

        public CachedMerchantCredentials(MerchantCredentials merchantCredentials, long cacheTime) {
            this.merchantCredentials = merchantCredentials;
            this.cacheTime = cacheTime;
        }

        public MerchantCredentials getMerchantCredentials() {
            return merchantCredentials;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > MERCHANT_CREDENTIALS_CACHE_TTL ||
                    !merchantCredentials.getActive();
        }
    }
}