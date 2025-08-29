package com.concerto.omnichannel.service;


import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class TransactionAnalyticsService {

    @Autowired
    private EntityManager entityManager;

    /**
     * Get cross-channel transaction summary
     */
    public List<Map<String, Object>> getChannelSummary(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
            SELECT 
                channel,
                COUNT(*) as transaction_count,
                SUM(amount) as total_amount,
                AVG(processing_time_ms) as avg_processing_time,
                COUNT(CASE WHEN success = true THEN 1 END) * 100.0 / COUNT(*) as success_rate
            FROM transaction_header
            WHERE request_timestamp >= :startDate 
            AND request_timestamp <= :endDate
            GROUP BY channel
            ORDER BY transaction_count DESC
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("startDate", startDate);
        query.setParameter("endDate", endDate);

        return query.getResultList();
    }

    /**
     * Get POS merchant performance
     */
    public List<Map<String, Object>> getPOSMerchantPerformance(LocalDateTime startDate) {
        String sql = """
            SELECT 
                pos.merchant_id,
                pos.merchant_name,
                COUNT(*) as transaction_count,
                SUM(th.amount) as total_amount,
                AVG(th.processing_time_ms) as avg_processing_time,
                COUNT(CASE WHEN th.success = true THEN 1 END) * 100.0 / COUNT(*) as success_rate
            FROM pos_transaction_details pos
            JOIN transaction_header th ON pos.transaction_header_id = th.id
            WHERE th.request_timestamp >= :startDate
            GROUP BY pos.merchant_id, pos.merchant_name
            ORDER BY total_amount DESC
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("startDate", startDate);

        return query.getResultList();
    }

    /**
     * Get UPI transaction patterns
     */
    public List<Map<String, Object>> getUPITransactionPatterns(LocalDateTime startDate) {
        String sql = """
            SELECT 
                upi.payment_mode,
                COUNT(*) as transaction_count,
                AVG(th.amount) as avg_amount,
                COUNT(CASE WHEN th.success = true THEN 1 END) * 100.0 / COUNT(*) as success_rate
            FROM upi_transaction_details upi
            JOIN transaction_header th ON upi.transaction_header_id = th.id
            WHERE th.request_timestamp >= :startDate
            GROUP BY upi.payment_mode
            ORDER BY transaction_count DESC
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("startDate", startDate);

        return query.getResultList();
    }
}

