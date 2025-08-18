package com.concerto.omnichannel.validation;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.service.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class BusinessRuleValidator {

    @Autowired
    private ConfigurationService configurationService;

    public void validateBusinessRules(TransactionRequest request) {
        List<String> violations = new ArrayList<>();

        // Check if channel is active
        if (!configurationService.isChannelActive(request.getChannel())) {
            violations.add("Channel " + request.getChannel() + " is currently inactive");
        }

        // Validate transaction limits
        validateTransactionLimits(request, violations);

        // Validate business hours
        validateBusinessHours(request, violations);

        // Validate channel-specific business rules
        validateChannelSpecificBusinessRules(request, violations);

        if (!violations.isEmpty()) {
            throw new RuntimeException("Business rule validation failed: " + String.join(", ", violations));
        }
    }

    private void validateTransactionLimits(TransactionRequest request, List<String> violations) {
        if (request.getPayload() != null && request.getPayload().getAmount() != null) {
            BigDecimal amount = request.getPayload().getAmount();

            // Get channel-specific limits
            BigDecimal minLimit = configurationService.getConfigValue(request.getChannel(), "minTransactionLimit", BigDecimal.class, BigDecimal.ZERO);
            BigDecimal maxLimit = configurationService.getConfigValue(request.getChannel(), "maxTransactionLimit", BigDecimal.class, new BigDecimal("1000000"));

            if (amount.compareTo(minLimit) < 0) {
                violations.add("Transaction amount below minimum limit of " + minLimit);
            }

            if (amount.compareTo(maxLimit) > 0) {
                violations.add("Transaction amount exceeds maximum limit of " + maxLimit);
            }
        }
    }

    private void validateBusinessHours(TransactionRequest request, List<String> violations) {
        String startTime = configurationService.getConfigValue(request.getChannel(), "businessHoursStart", "00:00");
        String endTime = configurationService.getConfigValue(request.getChannel(), "businessHoursEnd", "23:59");

        LocalTime currentTime = LocalTime.now();
        LocalTime businessStart = LocalTime.parse(startTime);
        LocalTime businessEnd = LocalTime.parse(endTime);

        if (currentTime.isBefore(businessStart) || currentTime.isAfter(businessEnd)) {
            violations.add("Transaction outside business hours for channel " + request.getChannel());
        }
    }

    private void validateChannelSpecificBusinessRules(TransactionRequest request, List<String> violations) {
        switch (request.getChannel().toUpperCase()) {
            case "ATM":
                validateAtmRules(request, violations);
                break;
            case "POS":
                validatePosRules(request, violations);
                break;
            case "UPI":
                validateUpiRules(request, violations);
                break;
            // Add more channel-specific validations
        }
    }

    private void validateAtmRules(TransactionRequest request, List<String> violations) {
        // ATM-specific business rules
        if ("withdrawal".equals(request.getOperation())) {
            BigDecimal dailyLimit = configurationService.getConfigValue("ATM", "dailyWithdrawalLimit", BigDecimal.class, new BigDecimal("50000"));
            // Additional ATM validations can be added here
        }
    }

    private void validatePosRules(TransactionRequest request, List<String> violations) {
        // POS-specific business rules
        if (request.getPayload() != null && request.getPayload().getMerchantId() == null) {
            violations.add("Merchant ID is mandatory for POS transactions");
        }
    }

    private void validateUpiRules(TransactionRequest request, List<String> violations) {
        // UPI-specific business rules
        if (request.getPayload() != null && request.getPayload().getAmount() != null) {
            BigDecimal maxUpiLimit = configurationService.getConfigValue("UPI", "maxTransactionAmount", BigDecimal.class, new BigDecimal("200000"));
            if (request.getPayload().getAmount().compareTo(maxUpiLimit) > 0) {
                violations.add("UPI transaction amount exceeds limit of " + maxUpiLimit);
            }
        }
    }
}

