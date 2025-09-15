package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.MerchantCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantCredentialsRepository extends JpaRepository<MerchantCredentials, Long> {

    List<MerchantCredentials> findByMerchantId(String merchantId);

    List<MerchantCredentials> findByChannel(String channel);

    Optional<MerchantCredentials> findByMerchantIdAndChannel(String merchantId, String channel);

    Optional<MerchantCredentials> findByMerchantIdAndChannelAndCredentialType(
            String merchantId, String channel, String credentialType);

    List<MerchantCredentials> findByMerchantIdAndIsActiveTrue(String merchantId);

    @Query("SELECT mc FROM MerchantCredentials mc WHERE mc.merchantId = :merchantId " +
            "AND mc.channel = :channel AND mc.isActive = true AND mc.status = 'ACTIVE'")
    Optional<MerchantCredentials> findActiveMerchantCredentials(
            @Param("merchantId") String merchantId, @Param("channel") String channel);

    @Query("SELECT mc FROM MerchantCredentials mc WHERE mc.validTo < CURRENT_DATE")
    List<MerchantCredentials> findExpiredCredentials();

    boolean existsByMerchantIdAndChannel(String merchantId, String channel);

    Optional<MerchantCredentials> findByMerchantIdAndChannelAndIsActiveTrue(String merchantId, String channelId);

}
