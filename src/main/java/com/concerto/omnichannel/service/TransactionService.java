package com.concerto.omnichannel.service;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.entity.TransactionDetail;
import com.concerto.omnichannel.entity.TransactionHeader;
import com.concerto.omnichannel.operations.OperationHandler;
import com.concerto.omnichannel.registry.OperationHandlerRegistry;
import com.concerto.omnichannel.repository.TransactionDetailRepository;
import com.concerto.omnichannel.repository.TransactionHeaderRepository;
import com.concerto.omnichannel.validation.BusinessRuleValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private TransactionHeaderRepository headerRepository;

    @Autowired
    private TransactionDetailRepository detailRepository;

    @Autowired
    private OperationHandlerRegistry handlerRegistry;

    @Autowired
    private BusinessRuleValidator businessRuleValidator;

    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        logger.info("Processing transaction for channel: {} operation: {}",
                request.getChannel(), request.getOperation());

        // 1. Validate business rules
        businessRuleValidator.validateBusinessRules(request);

        // 2. Create and save transaction header
        TransactionHeader header = createTransactionHeader(request);
        header = headerRepository.save(header);
        logger.debug("Transaction header created with ID: {}", header.getId());

        // 3. Save transaction details
        saveTransactionDetails(header, request);

        // 4. Resolve correct OperationHandler
        OperationHandler handler = handlerRegistry.getHandler(request.getChannel(), request.getOperation());
        if (handler == null) {
            return handleUnsupportedOperation(header, request);
        }

        // 5. Invoke the operation
        try {
            header.setStatus("PROCESSING");
            headerRepository.save(header);

            TransactionResponse response = handler.handle(request);

            // Update transaction status based on response
            updateTransactionStatus(header, response);

            // Build final response
            return buildSuccessResponse(header, response);

        } catch (Exception e) {
            logger.error("Transaction processing failed for ID: {}", header.getId(), e);
            return handleTransactionFailure(header, e);
        }
    }

    @Cacheable(value = "transactionStatus", key = "#transactionId")
    public TransactionResponse getTransactionStatus(Long transactionId) {
        logger.debug("Retrieving transaction status for ID: {}", transactionId);

        Optional<TransactionHeader> headerOpt = headerRepository.findById(transactionId);
        if (headerOpt.isEmpty()) {
            throw new RuntimeException("Transaction not found with ID: " + transactionId);
        }

        TransactionHeader header = headerOpt.get();
        return buildTransactionStatusResponse(header);
    }

    public TransactionResponse getTransactionStatusByCorrelationId(String correlationId) {
        logger.debug("Retrieving transaction status for correlation ID: {}", correlationId);

        Optional<TransactionHeader> headerOpt = headerRepository.findByCorrelationId(correlationId);
        if (headerOpt.isEmpty()) {
            throw new RuntimeException("Transaction not found with correlation ID: " + correlationId);
        }

        TransactionHeader header = headerOpt.get();
        return buildTransactionStatusResponse(header);
    }

    @Transactional
    public void updateTransactionStatus(Long transactionId, String status, String errorMessage) {
        Optional<TransactionHeader> headerOpt = headerRepository.findById(transactionId);
        if (headerOpt.isPresent()) {
            TransactionHeader header = headerOpt.get();
            header.setStatus(status);
            if (errorMessage != null) {
                header.setErrorMessage(errorMessage);
            }
            header.setResponseTimestamp(LocalDateTime.now());
            headerRepository.save(header);

            logger.info("Transaction status updated to {} for ID: {}", status, transactionId);
        }
    }

    public Map<String, Long> getTransactionStatistics(String channel, LocalDateTime since) {
        List<Object[]> results = headerRepository.findTransactionStatusCountsByChannel(channel, since);
        Map<String, Long> statistics = new HashMap<>();

        for (Object[] result : results) {
            String status = (String) result[0];
            Long count = (Long) result[1];
            statistics.put(status, count);
        }

        return statistics;
    }

    public Double getAverageProcessingTime(String channel, LocalDateTime since) {
        return headerRepository.findAverageProcessingTimeByChannel(channel, since);
    }

    public List<TransactionHeader> getStaleTransactions(int hours) {
        List<String> processingStatuses = List.of("RECEIVED", "PROCESSING", "AUTHENTICATED");
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hours);
        return headerRepository.findStaleTransactions(processingStatuses, cutoffTime);
    }

    // Private helper methods
    private TransactionHeader createTransactionHeader(TransactionRequest request) {
        TransactionHeader header = new TransactionHeader();
        header.setChannel(request.getChannel());
        header.setOperation(request.getOperation());
        header.setRequestTimestamp(LocalDateTime.now());
        header.setStatus("RECEIVED");

        if (request.getPayload() != null) {
            header.setTransactionType(request.getPayload().getTransactionType());
            header.setAmount(request.getPayload().getAmount());
            header.setCurrency(request.getPayload().getCurrency());
            header.setMerchantId(request.getPayload().getMerchantId());
            header.setTerminalId(request.getPayload().getTerminalId());
        }

        return header;
    }

    private void saveTransactionDetails(TransactionHeader header, TransactionRequest request) {
        if (request.getPayload() != null && request.getPayload().getAdditionalFields() != null) {
            for (Map.Entry<String, Object> entry : request.getPayload().getAdditionalFields().entrySet()) {
                TransactionDetail detail = new TransactionDetail();
                detail.setTransactionHeader(header);
                detail.setFieldName(entry.getKey());
                detail.setFieldValue(entry.getValue().toString());
                detail.setFieldType(entry.getValue().getClass().getSimpleName());

                // Mark sensitive fields
                if (isSensitiveField(entry.getKey())) {
                    detail.setSensitive(true);
                    detail.setEncrypted(true);
                    // In production, encrypt the value here
                }

                detailRepository.save(detail);
            }
        }
    }

    private boolean isSensitiveField(String fieldName) {
        return fieldName.toLowerCase().contains("card") ||
                fieldName.toLowerCase().contains("pan") ||
                fieldName.toLowerCase().contains("pin") ||
                fieldName.toLowerCase().contains("cvv") ||
                fieldName.toLowerCase().contains("password") ||
                fieldName.toLowerCase().contains("secret");
    }

    private TransactionResponse handleUnsupportedOperation(TransactionHeader header, TransactionRequest request) {
        header.setStatus("FAILED");
        header.setErrorMessage("Unsupported channel/operation");
        header.setErrorCode("UNSUPPORTED_OPERATION");
        header.setResponseTimestamp(LocalDateTime.now());
        headerRepository.save(header);

        logger.warn("Unsupported operation: {} for channel: {}", request.getOperation(), request.getChannel());

        return TransactionResponse.failure(
                header.getId(),
                header.getCorrelationId(),
                "Unsupported channel/operation: " + request.getChannel() + "/" + request.getOperation(),
                "UNSUPPORTED_OPERATION"
        );
    }

    private void updateTransactionStatus(TransactionHeader header, TransactionResponse response) {
        if (response.isSuccess()) {
            header.setStatus("SUCCESS");
            header.setExternalReference(response.getExternalReference());
        } else {
            header.setStatus("FAILED");
            header.setErrorMessage(response.getErrorMessage());
            header.setErrorCode(response.getErrorCode());
        }

        header.setResponseTimestamp(LocalDateTime.now());
        headerRepository.save(header);
    }

    private TransactionResponse buildSuccessResponse(TransactionHeader header, TransactionResponse response) {
        response.setTransactionId(header.getId());
        response.setCorrelationId(header.getCorrelationId());
        response.setChannel(header.getChannel());
        response.setOperation(header.getOperation());
        response.setProcessingTimeMs(header.getProcessingTimeMs());

        logger.info("Transaction completed successfully with ID: {}", header.getId());
        return response;
    }

    private TransactionResponse handleTransactionFailure(TransactionHeader header, Exception e) {
        header.setStatus("FAILED");
        header.setErrorMessage(e.getMessage());
        header.setErrorCode("PROCESSING_ERROR");
        header.setResponseTimestamp(LocalDateTime.now());
        headerRepository.save(header);

        TransactionResponse response = TransactionResponse.failure(
                header.getId(),
                header.getCorrelationId(),
                "Transaction processing failed: " + e.getMessage(),
                "PROCESSING_ERROR"
        );

        response.setChannel(header.getChannel());
        response.setOperation(header.getOperation());
        response.setProcessingTimeMs(header.getProcessingTimeMs());

        return response;
    }

    private TransactionResponse buildTransactionStatusResponse(TransactionHeader header) {
        TransactionResponse response = new TransactionResponse();
        response.setTransactionId(header.getId());
        response.setCorrelationId(header.getCorrelationId());
        response.setChannel(header.getChannel());
        response.setOperation(header.getOperation());
        response.setSuccess("SUCCESS".equals(header.getStatus()));
        response.setErrorMessage(header.getErrorMessage());
        response.setErrorCode(header.getErrorCode());
        response.setExternalReference(header.getExternalReference());
        response.setProcessingTimeMs(header.getProcessingTimeMs());
        response.setResponseTime(header.getResponseTimestamp());

        // Add status-specific information
        response.addAdditionalData("status", header.getStatus());
        response.addAdditionalData("requestTime", header.getRequestTimestamp());
        response.addAdditionalData("amount", header.getAmount());
        response.addAdditionalData("currency", header.getCurrency());
        response.addAdditionalData("merchantId", header.getMerchantId());
        response.addAdditionalData("terminalId", header.getTerminalId());

        return response;
    }
}