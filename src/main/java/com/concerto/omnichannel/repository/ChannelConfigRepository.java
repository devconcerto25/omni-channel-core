package com.concerto.omnichannel.repository;


import com.concerto.omnichannel.entity.ChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelConfigRepository extends JpaRepository<ChannelConfig, Long> {

    Optional<ChannelConfig> findByChannelIdAndConfigKey(String channelId, String configKey);

    Optional<ChannelConfig> findByChannelIdAndConfigKeyAndActiveTrue(String channelId, String configKey);

    List<ChannelConfig> findByChannelIdAndActiveTrue(String channelId);

    List<ChannelConfig> findByChannelId(String channelId);

    @Modifying
    @Query("DELETE FROM ChannelConfig c WHERE c.channelId = :channelId AND c.configKey = :configKey")
    void deleteByChannelIdAndConfigKey(@Param("channelId") String channelId, @Param("configKey") String configKey);

    @Query("SELECT DISTINCT c.channelId FROM ChannelConfig c WHERE c.active = true")
    List<String> findAllActiveChannels();

    @Query("SELECT c FROM ChannelConfig c WHERE c.channelId = :channelId AND c.configKey LIKE :keyPattern AND c.active = true")
    List<ChannelConfig> findByChannelIdAndConfigKeyPattern(@Param("channelId") String channelId, @Param("keyPattern") String keyPattern);
}
