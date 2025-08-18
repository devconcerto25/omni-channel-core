package com.concerto.omnichannel.service;

import com.concerto.omnichannel.entity.ChannelConfig;
import com.concerto.omnichannel.repository.ChannelConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationService.class);
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String ENCRYPTION_KEY = "MySecretKey12345"; // In production, use proper key management

    @Autowired
    private ChannelConfigRepository channelConfigRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Get configuration value for a channel and key
     */
    @Cacheable(value = "channelConfigs", key = "#channelId + ':' + #configKey")
    public String getConfigValue(String channelId, String configKey) {
        Optional<ChannelConfig> configOpt = channelConfigRepository
                .findByChannelIdAndConfigKeyAndActiveTrue(channelId, configKey);

        if (configOpt.isEmpty()) {
            logger.warn("Configuration not found for channel: {} key: {}", channelId, configKey);
            return null;
        }

        ChannelConfig config = configOpt.get();
        String value = config.getConfigValue();

        // Decrypt if encrypted
        if (config.isEncrypted()) {
            value = decrypt(value);
        }

        return value;
    }

    /**
     * Get configuration value with default fallback
     */
    public String getConfigValue(String channelId, String configKey, String defaultValue) {
        String value = getConfigValue(channelId, configKey);
        return value != null ? value : defaultValue;
    }

    /**
     * Get configuration value as specific type
     */
    public <T> T getConfigValue(String channelId, String configKey, Class<T> type) {
        String value = getConfigValue(channelId, configKey);
        if (value == null) return null;

        return convertValue(value, type);
    }

    /**
     * Get configuration value as specific type with default
     */
    public <T> T getConfigValue(String channelId, String configKey, Class<T> type, T defaultValue) {
        T value = getConfigValue(channelId, configKey, type);
        return value != null ? value : defaultValue;
    }

    /**
     * Get all configurations for a channel
     */
    @Cacheable(value = "channelAllConfigs", key = "#channelId")
    public Map<String, Object> getAllConfigs(String channelId) {
        List<ChannelConfig> configs = channelConfigRepository.findByChannelIdAndActiveTrue(channelId);
        Map<String, Object> configMap = new HashMap<>();

        for (ChannelConfig config : configs) {
            String value = config.getConfigValue();

            // Decrypt if encrypted
            if (config.isEncrypted()) {
                value = decrypt(value);
            }

            // Convert based on type
            Object convertedValue = convertValueByType(value, config.getConfigType());
            configMap.put(config.getConfigKey(), convertedValue);
        }

        return configMap;
    }

    /**
     * Get timeout configuration for channel
     */
    public Duration getChannelTimeout(String channelId) {
        Integer timeoutMs = getConfigValue(channelId, "timeout", Integer.class, 30000);
        return Duration.ofMillis(timeoutMs);
    }

    /**
     * Get retry configuration for channel
     */
    public int getChannelMaxRetries(String channelId) {
        return getConfigValue(channelId, "maxRetries", Integer.class, 2);
    }

    /**
     * Check if circuit breaker is enabled for channel
     */
    public boolean isCircuitBreakerEnabled(String channelId) {
        return getConfigValue(channelId, "circuitBreaker", Boolean.class, true);
    }

    /**
     * Get batch size for channel processing
     */
    public int getBatchSize(String channelId) {
        return getConfigValue(channelId, "batchSize", Integer.class, 100);
    }

    /**
     * Save or update configuration
     */
    @Transactional
    @CacheEvict(value = {"channelConfigs", "channelAllConfigs"}, key = "#channelId")
    public void saveConfig(String channelId, String configKey, String configValue,
                           String configType, boolean encrypted, String updatedBy) {

        Optional<ChannelConfig> existingConfigOpt = channelConfigRepository
                .findByChannelIdAndConfigKey(channelId, configKey);

        ChannelConfig config;
        if (existingConfigOpt.isPresent()) {
            config = existingConfigOpt.get();
            config.setUpdatedBy(updatedBy);
        } else {
            config = new ChannelConfig();
            config.setChannelId(channelId);
            config.setConfigKey(configKey);
            config.setCreatedBy(updatedBy);
        }

        // Encrypt if required
        String valueToStore = encrypted ? encrypt(configValue) : configValue;

        config.setConfigValue(valueToStore);
        config.setConfigType(configType);
        config.setEncrypted(encrypted);
        config.setActive(true);

        channelConfigRepository.save(config);
        logger.info("Configuration saved for channel: {} key: {}", channelId, configKey);
    }

    /**
     * Delete configuration
     */
    @Transactional
    @CacheEvict(value = {"channelConfigs", "channelAllConfigs"}, key = "#channelId")
    public void deleteConfig(String channelId, String configKey) {
        channelConfigRepository.deleteByChannelIdAndConfigKey(channelId, configKey);
        logger.info("Configuration deleted for channel: {} key: {}", channelId, configKey);
    }

    /**
     * Deactivate configuration
     */
    @Transactional
    @CacheEvict(value = {"channelConfigs", "channelAllConfigs"}, key = "#channelId")
    public void deactivateConfig(String channelId, String configKey) {
        Optional<ChannelConfig> configOpt = channelConfigRepository
                .findByChannelIdAndConfigKey(channelId, configKey);

        if (configOpt.isPresent()) {
            ChannelConfig config = configOpt.get();
            config.setActive(false);
            channelConfigRepository.save(config);
            logger.info("Configuration deactivated for channel: {} key: {}", channelId, configKey);
        }
    }

    /**
     * Check if channel is active
     */
    public boolean isChannelActive(String channelId) {
        return getConfigValue(channelId, "active", Boolean.class, false);
    }

    /**
     * Get channel-specific mapping configurations
     */
    public Map<String, String> getChannelMappings(String channelId) {
        String mappingJson = getConfigValue(channelId, "fieldMappings");
        if (mappingJson == null) return new HashMap<>();

        try {
            return objectMapper.readValue(mappingJson, Map.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse channel mappings for channel: {}", channelId, e);
            return new HashMap<>();
        }
    }

    /**
     * Get connector endpoint for channel
     */
    public String getConnectorEndpoint(String channelId) {
        return getConfigValue(channelId, "connectorEndpoint");
    }

    /**
     * Get authentication configuration for channel
     */
    public Map<String, Object> getAuthConfig(String channelId) {
        Map<String, Object> authConfig = new HashMap<>();
        authConfig.put("authType", getConfigValue(channelId, "authType", "basic"));
        authConfig.put("username", getConfigValue(channelId, "authUsername"));
        authConfig.put("password", getConfigValue(channelId, "authPassword"));
        authConfig.put("apiKey", getConfigValue(channelId, "apiKey"));
        authConfig.put("tokenUrl", getConfigValue(channelId, "tokenUrl"));

        return authConfig;
    }

    // Private helper methods
    @SuppressWarnings("unchecked")
    private <T> T convertValue(String value, Class<T> type) {
        try {
            if (type == String.class) {
                return (T) value;
            } else if (type == Integer.class) {
                return (T) Integer.valueOf(value);
            } else if (type == Long.class) {
                return (T) Long.valueOf(value);
            } else if (type == Boolean.class) {
                return (T) Boolean.valueOf(value);
            } else if (type == Double.class) {
                return (T) Double.valueOf(value);
            } else {
                // For complex objects, try JSON parsing
                return objectMapper.readValue(value, type);
            }
        } catch (Exception e) {
            logger.error("Failed to convert config value: {} to type: {}", value, type.getSimpleName(), e);
            return null;
        }
    }

    private Object convertValueByType(String value, String configType) {
        if (configType == null) return value;

        switch (configType.toUpperCase()) {
            case "INTEGER":
                return Integer.valueOf(value);
            case "LONG":
                return Long.valueOf(value);
            case "BOOLEAN":
                return Boolean.valueOf(value);
            case "DOUBLE":
                return Double.valueOf(value);
            case "JSON":
                try {
                    return objectMapper.readValue(value, Object.class);
                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse JSON config value: {}", value, e);
                    return value;
                }
            default:
                return value;
        }
    }

    private String encrypt(String plainText) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), ENCRYPTION_ALGORITHM);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            logger.error("Failed to encrypt configuration value", e);
            return plainText; // Return plain text if encryption fails
        }
    }

    private String decrypt(String encryptedText) {
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(StandardCharsets.UTF_8), ENCRYPTION_ALGORITHM);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);

            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Failed to decrypt configuration value", e);
            return encryptedText; // Return encrypted text if decryption fails
        }
    }
}

