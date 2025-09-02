package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.MerchantChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantChannelConfigRepository extends JpaRepository<MerchantChannelConfig, Long> {

    List<MerchantChannelConfig> findByMerchantId(String merchantId);

    Optional<MerchantChannelConfig> findByMerchantIdAndChannel(String merchantId, String channel);

    List<MerchantChannelConfig> findByChannel(String channel);

    List<MerchantChannelConfig> findByMerchantIdAndIsEnabledTrue(String merchantId);

    @Query("SELECT mcc FROM MerchantChannelConfig mcc WHERE mcc.merchantId = :merchantId " +
            "AND mcc.channel = :channel AND mcc.isEnabled = true")
    Optional<MerchantChannelConfig> findEnabledChannelConfig(
            @Param("merchantId") String merchantId, @Param("channel") String channel);

    @Query("SELECT mcc FROM MerchantChannelConfig mcc WHERE mcc.isEnabled = true")
    List<MerchantChannelConfig> findAllEnabledConfigs();

    boolean existsByMerchantIdAndChannel(String merchantId, String channel);
}
