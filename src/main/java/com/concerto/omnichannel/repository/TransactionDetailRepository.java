package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.TransactionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionDetailRepository extends JpaRepository<TransactionDetail, Long> {

    List<TransactionDetail> findByTransactionHeaderId(Long transactionHeaderId);

    List<TransactionDetail> findByTransactionHeaderIdAndSensitiveTrue(Long transactionHeaderId);

    List<TransactionDetail> findByTransactionHeaderIdAndEncryptedTrue(Long transactionHeaderId);

    Optional<TransactionDetail> findByTransactionHeaderIdAndFieldName(Long transactionHeaderId, String fieldName);

    @Query("SELECT d FROM TransactionDetail d WHERE d.transactionHeader.correlationId = :correlationId")
    List<TransactionDetail> findByCorrelationId(@Param("correlationId") String correlationId);

    @Query("SELECT d FROM TransactionDetail d WHERE d.transactionHeader.correlationId = :correlationId AND d.fieldName = :fieldName")
    Optional<TransactionDetail> findByCorrelationIdAndFieldName(@Param("correlationId") String correlationId, @Param("fieldName") String fieldName);

    @Query("SELECT d FROM TransactionDetail d WHERE d.transactionHeader.channel = :channel AND d.fieldName = :fieldName")
    List<TransactionDetail> findByChannelAndFieldName(@Param("channel") String channel, @Param("fieldName") String fieldName);

    @Query("SELECT DISTINCT d.fieldName FROM TransactionDetail d WHERE d.transactionHeader.channel = :channel")
    List<String> findDistinctFieldNamesByChannel(@Param("channel") String channel);

    @Query("DELETE FROM TransactionDetail d WHERE d.transactionHeader.id = :transactionHeaderId")
    void deleteByTransactionHeaderId(@Param("transactionHeaderId") Long transactionHeaderId);
}
