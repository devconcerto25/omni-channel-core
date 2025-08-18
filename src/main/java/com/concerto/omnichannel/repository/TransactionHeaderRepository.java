package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.TransactionHeader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionHeaderRepository extends JpaRepository<TransactionHeader, Long> {

    Optional<TransactionHeader> findByCorrelationId(String correlationId);

    List<TransactionHeader> findByChannel(String channel);

    List<TransactionHeader> findByChannelAndStatus(String channel, String status);

    List<TransactionHeader> findByStatus(String status);

    @Query("SELECT t FROM TransactionHeader t WHERE t.requestTimestamp BETWEEN :startDate AND :endDate")
    List<TransactionHeader> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT t FROM TransactionHeader t WHERE t.channel = :channel AND t.requestTimestamp BETWEEN :startDate AND :endDate")
    List<TransactionHeader> findByChannelAndDateRange(
            @Param("channel") String channel,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT t FROM TransactionHeader t WHERE t.clientId = :clientId ORDER BY t.requestTimestamp DESC")
    Page<TransactionHeader> findByClientId(@Param("clientId") String clientId, Pageable pageable);

    @Query("SELECT t FROM TransactionHeader t WHERE t.merchantId = :merchantId AND t.requestTimestamp BETWEEN :startDate AND :endDate")
    List<TransactionHeader> findByMerchantIdAndDateRange(
            @Param("merchantId") String merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(t) FROM TransactionHeader t WHERE t.channel = :channel AND t.status = :status AND t.requestTimestamp >= :since")
    long countByChannelAndStatusAndRequestTimestampAfter(
            @Param("channel") String channel,
            @Param("status") String status,
            @Param("since") LocalDateTime since
    );

    @Query("SELECT t FROM TransactionHeader t WHERE t.status IN :statuses AND t.requestTimestamp < :before")
    List<TransactionHeader> findStaleTransactions(@Param("statuses") List<String> statuses, @Param("before") LocalDateTime before);

    @Query("SELECT DISTINCT t.channel FROM TransactionHeader t")
    List<String> findDistinctChannels();

    @Query("SELECT AVG(t.processingTimeMs) FROM TransactionHeader t WHERE t.channel = :channel AND t.status = 'SUCCESS' AND t.requestTimestamp >= :since")
    Double findAverageProcessingTimeByChannel(@Param("channel") String channel, @Param("since") LocalDateTime since);

    @Query("SELECT t.status, COUNT(t) FROM TransactionHeader t WHERE t.channel = :channel AND t.requestTimestamp >= :since GROUP BY t.status")
    List<Object[]> findTransactionStatusCountsByChannel(@Param("channel") String channel, @Param("since") LocalDateTime since);
}