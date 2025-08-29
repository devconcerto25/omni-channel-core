package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.POSTransactionDetails;
import com.concerto.omnichannel.entity.TransactionHeader;
import com.concerto.omnichannel.entity.UPITransactionDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UPITransactionRepository extends JpaRepository<UPITransactionDetails, Long> {

    Optional<UPITransactionDetails> findByTransactionHeader(TransactionHeader transactionHeader);

    Optional<UPITransactionDetails> findByUpiTransactionId(String upiTransactionId);

    List<UPITransactionDetails> findByPayerVPA(String payerVPA);

    List<UPITransactionDetails> findByPayeeVPA(String payeeVPA);

    @Query("SELECT u FROM UPITransactionDetails u WHERE u.payerVPA = :vpa OR u.payeeVPA = :vpa")
    List<UPITransactionDetails> findByVPA(@Param("vpa") String vpa);
}
