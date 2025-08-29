package com.concerto.omnichannel.repository;


import com.concerto.omnichannel.entity.POSTransactionDetails;
import com.concerto.omnichannel.entity.TransactionHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface POSTransactionRepository extends JpaRepository<POSTransactionDetails, Long> {

    Optional<POSTransactionDetails> findByTransactionHeader(TransactionHeader transactionHeader);

    Optional<POSTransactionDetails> findByRrn(String rrn);

    List<POSTransactionDetails> findByMerchantIdAndTerminalId(String merchantId, String terminalId);

    @Query("SELECT p FROM POSTransactionDetails p WHERE p.merchantId = :merchantId " +
            "AND p.createdDate >= :startDate AND p.createdDate <= :endDate")
    List<POSTransactionDetails> findByMerchantAndDateRange(
            @Param("merchantId") String merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT p FROM POSTransactionDetails p JOIN p.transactionHeader th " +
            "WHERE th.status = 'SUCCESS' AND p.createdDate >= :startDate")
    List<POSTransactionDetails> findSuccessfulTransactionsSince(@Param("startDate") LocalDateTime startDate);
}

