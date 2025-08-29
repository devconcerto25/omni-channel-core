package com.concerto.omnichannel.service;

import com.concerto.omnichannel.entity.TransactionHeader;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnhancedMainOrchestrator extends MainOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedMainOrchestrator.class);

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private ChannelSpecificTransactionService channelSpecificService;

    @Transactional
    public TransactionResponse orchestrate(TransactionRequest request, String clientId,
                                           String clientSecret, String authToken, String correlationId) {

        // Call parent orchestrator logic
        TransactionResponse response = super.orchestrate(request, clientId, clientSecret, authToken);

        try {
            // Get the transaction header (you'll need to expose this from parent class)
            TransactionHeader header = getTransactionHeaderByCorrelationId(correlationId);

            if (header != null) {
                // Save channel-specific details
                channelSpecificService.saveChannelSpecificDetails(header, request, response);
                logger.debug("Channel-specific details saved for correlation ID: {}", correlationId);
            }

        } catch (Exception e) {
            logger.error("Failed to save channel-specific details, but transaction completed", e);
            // Don't fail the transaction due to channel-specific storage issues
        }

        return response;
    }

    // Add method to retrieve transaction header
    protected TransactionHeader getTransactionHeaderByCorrelationId(String correlationId) {
        // Implementation depends on your existing TransactionService
        return transactionService.findByCorrelationId(correlationId);
    }
}

