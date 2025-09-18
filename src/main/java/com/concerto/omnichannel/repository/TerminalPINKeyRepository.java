package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.ChannelConfig;
import com.concerto.omnichannel.entity.TerminalPINKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TerminalPINKeyRepository extends JpaRepository<TerminalPINKey, Long> {
    /**
     * Find PIN key by merchant ID and terminal ID
     */
    Optional<TerminalPINKey> findByMerchantIdAndTerminalId(String merchantId, String terminalId);

    /**
     * Find all PIN keys for a merchant
     */
    List<TerminalPINKey> findByMerchantId(String merchantId);

    /**
     * Find all active PIN keys for a merchant
     */
    @Query("SELECT t FROM TerminalPINKey t WHERE t.merchantId = :merchantId AND t.keyStatus = 'ACTIVE'")
    List<TerminalPINKey> findActiveKeysByMerchantId(@Param("merchantId") String merchantId);

    /**
     * Find PIN keys by status
     */
    List<TerminalPINKey> findByKeyStatus(String keyStatus);

    /**
     * Find PIN keys expiring before given date
     */
    @Query("SELECT t FROM TerminalPINKey t WHERE t.expiresDate < :expiryDate AND t.keyStatus = 'ACTIVE'")
    List<TerminalPINKey> findKeysExpiringBefore(@Param("expiryDate") LocalDateTime expiryDate);

    /**
     * Find PIN keys by key type
     */
    List<TerminalPINKey> findByKeyType(String keyType);

    /**
     * Find PIN keys by key version
     */
    List<TerminalPINKey> findByKeyVersion(String keyVersion);

    /**
     * Check if PIN key exists for terminal
     */
    boolean existsByMerchantIdAndTerminalId(String merchantId, String terminalId);

    /**
     * Update key status
     */
    @Modifying
    @Query("UPDATE TerminalPINKey t SET t.keyStatus = :status, t.updatedDate = :updatedDate, t.updatedBy = :updatedBy " +
            "WHERE t.merchantId = :merchantId AND t.terminalId = :terminalId")
    int updateKeyStatus(@Param("merchantId") String merchantId,
                        @Param("terminalId") String terminalId,
                        @Param("status") String status,
                        @Param("updatedDate") LocalDateTime updatedDate,
                        @Param("updatedBy") String updatedBy);

    /**
     * Update usage count and last used date
     */
    @Modifying
    @Query("UPDATE TerminalPINKey t SET t.usageCount = t.usageCount + 1, t.lastUsedDate = :lastUsedDate " +
            "WHERE t.merchantId = :merchantId AND t.terminalId = :terminalId")
    int incrementUsageCount(@Param("merchantId") String merchantId,
                            @Param("terminalId") String terminalId,
                            @Param("lastUsedDate") LocalDateTime lastUsedDate);

    /**
     * Deactivate expired keys
     */
    @Modifying
    @Query("UPDATE TerminalPINKey t SET t.keyStatus = 'EXPIRED', t.updatedDate = :updatedDate " +
            "WHERE t.expiresDate < :currentDate AND t.keyStatus = 'ACTIVE'")
    int deactivateExpiredKeys(@Param("currentDate") LocalDateTime currentDate,
                              @Param("updatedDate") LocalDateTime updatedDate);

    /**
     * Find terminals with missing PIN keys for a merchant
     */
    @Query("SELECT DISTINCT pt.terminalId FROM POSTransactionDetails pt " +
            "WHERE pt.merchantId = :merchantId " +
            "AND NOT EXISTS (SELECT 1 FROM TerminalPINKey tpk " +
            "               WHERE tpk.merchantId = pt.merchantId " +
            "               AND tpk.terminalId = pt.terminalId " +
            "               AND tpk.keyStatus = 'ACTIVE')")
    List<String> findTerminalsWithoutPINKeys(@Param("merchantId") String merchantId);

    /**
     * Get key statistics for monitoring
     */
    @Query("SELECT t.keyStatus, COUNT(t) FROM TerminalPINKey t GROUP BY t.keyStatus")
    List<Object[]> getKeyStatusStatistics();

    /**
     * Get key usage statistics
     */
    @Query("SELECT t.merchantId, COUNT(t), AVG(t.usageCount), MAX(t.lastUsedDate) " +
            "FROM TerminalPINKey t WHERE t.keyStatus = 'ACTIVE' GROUP BY t.merchantId")
    List<Object[]> getKeyUsageStatistics();

    /**
     * Find most frequently used keys
     */
    @Query("SELECT t FROM TerminalPINKey t WHERE t.keyStatus = 'ACTIVE' ORDER BY t.usageCount DESC")
    List<TerminalPINKey> findMostUsedKeys();

    /**
     * Find keys not used recently
     */
    @Query("SELECT t FROM TerminalPINKey t WHERE t.keyStatus = 'ACTIVE' " +
            "AND (t.lastUsedDate IS NULL OR t.lastUsedDate < :cutoffDate)")
    List<TerminalPINKey> findUnusedKeys(@Param("cutoffDate") LocalDateTime cutoffDate);
}
