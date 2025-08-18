package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.ClientCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCredentialsRepository extends JpaRepository<ClientCredentials, Long> {

    Optional<ClientCredentials> findByClientIdAndChannelId(String clientId, String channelId);

    Optional<ClientCredentials> findByClientIdAndChannelIdAndActiveTrue(String clientId, String channelId);

    List<ClientCredentials> findByClientId(String clientId);

    List<ClientCredentials> findByChannelId(String channelId);

    List<ClientCredentials> findByActiveTrue();

    @Query("SELECT c FROM ClientCredentials c WHERE c.expiryDate < :now AND c.active = true")
    List<ClientCredentials> findExpiredCredentials(@Param("now") LocalDateTime now);

    @Query("SELECT c FROM ClientCredentials c WHERE c.expiryDate BETWEEN :now AND :warningDate AND c.active = true")
    List<ClientCredentials> findCredentialsNearExpiry(@Param("now") LocalDateTime now, @Param("warningDate") LocalDateTime warningDate);

    @Query("SELECT COUNT(c) FROM ClientCredentials c WHERE c.clientId = :clientId AND c.active = true")
    long countActiveCredentialsByClientId(@Param("clientId") String clientId);
}

