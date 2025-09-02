package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.MerchantTerminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantTerminalRepository extends JpaRepository<MerchantTerminal, Long> {

    List<MerchantTerminal> findByMerchantId(String merchantId);

    Optional<MerchantTerminal> findByMerchantIdAndTerminalId(String merchantId, String terminalId);

    List<MerchantTerminal> findByTerminalType(String terminalType);

    List<MerchantTerminal> findByMerchantIdAndIsActiveTrue(String merchantId);

    @Query("SELECT mt FROM MerchantTerminal mt WHERE mt.merchantId = :merchantId " +
            "AND mt.terminalType = :terminalType AND mt.isActive = true")
    List<MerchantTerminal> findActiveTerminalsByType(
            @Param("merchantId") String merchantId, @Param("terminalType") String terminalType);

    @Query("SELECT mt FROM MerchantTerminal mt WHERE mt.city = :city AND mt.status = 'ACTIVE'")
    List<MerchantTerminal> findActiveTerminalsByCity(@Param("city") String city);

    boolean existsByMerchantIdAndTerminalId(String merchantId, String terminalId);
}

