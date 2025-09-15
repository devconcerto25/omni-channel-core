package com.concerto.omnichannel.repository;

import com.concerto.omnichannel.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// 1. Merchant Info Repository
@Repository
public interface MerchantInfoRepository extends JpaRepository<MerchantInfo, Long> {

    Optional<MerchantInfo> findByMerchantId(String merchantId);

    List<MerchantInfo> findByStatus(String status);

    List<MerchantInfo> findByIsActiveTrue();

    List<MerchantInfo> findByMerchantType(String merchantType);

    List<MerchantInfo> findByBusinessCategory(String businessCategory);

    @Query("SELECT m FROM MerchantInfo m WHERE m.merchantName LIKE %:name%")
    List<MerchantInfo> findByMerchantNameContaining(@Param("name") String name);

    @Query("SELECT m FROM MerchantInfo m WHERE m.city = :city AND m.status = 'ACTIVE'")
    List<MerchantInfo> findActiveMerchantsByCity(@Param("city") String city);

    boolean existsByMerchantId(String merchantId);

    Optional<MerchantInfo> findByMerchantIdAndIsActiveTrue(String merchantId);
}