package com.concerto.omnichannel.utils;

import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.dto.Payload;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ValidationUtils {

    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^[0-9]{12,19}$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^[0-9A-Z]{6,20}$");

    public static void validateTransactionRequest(TransactionRequest request) {
        List<String> errors = new ArrayList<>();

        // Validate channel
        if (!StringUtils.hasText(request.getChannel())) {
            errors.add("Channel is required");
        } else if (!isValidChannel(request.getChannel())) {
            errors.add("Invalid channel. Allowed values: POS, ATM, UPI, BBPS, PG");
        }

        // Validate operation
        if (!StringUtils.hasText(request.getOperation())) {
            errors.add("Operation is required");
        } else if (request.getOperation().length() > 100) {
            errors.add("Operation must not exceed 100 characters");
        }

        // Validate payload
        if (request.getPayload() == null) {
            errors.add("Payload is required");
        } else {
            validatePayload(request.getPayload(), errors);
        }

        // Channel-specific validations
        if (request.getPayload() != null) {
            validateChannelSpecificRules(request.getChannel(), request.getPayload(), errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }

    private static void validatePayload(Payload payload, List<String> errors) {
        // Validate transaction type
        if (!StringUtils.hasText(payload.getTransactionType())) {
            errors.add("Transaction type is required");
        } else if (payload.getTransactionType().length() > 100) {
            errors.add("Transaction type must not exceed 100 characters");
        }

        // Validate amount
        if (payload.getAmount() != null) {
            if (payload.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Amount must be greater than 0");
            } else if (payload.getAmount().compareTo(new BigDecimal("999999999.99")) > 0) {
                errors.add("Amount cannot exceed 999999999.99");
            }
        }

        // Validate currency
        if (StringUtils.hasText(payload.getCurrency())) {
            if (!CURRENCY_PATTERN.matcher(payload.getCurrency()).matches()) {
                errors.add("Currency must be a 3-letter ISO code");
            }
        }

        // Validate card number if present
        if (StringUtils.hasText(payload.getCardNumber())) {
            if (!CARD_NUMBER_PATTERN.matcher(payload.getCardNumber()).matches()) {
                errors.add("Card number must be 12-19 digits");
            } else if (!isValidLuhn(payload.getCardNumber())) {
                errors.add("Invalid card number (Luhn check failed)");
            }
        }

        // Validate account number if present
        if (StringUtils.hasText(payload.getAccountNumber())) {
            if (payload.getAccountNumber().length() > 100) {
                errors.add("Account number must not exceed 100 characters");
            }
        }

        // Validate merchant ID
        if (StringUtils.hasText(payload.getMerchantId())) {
            if (payload.getMerchantId().length() > 100) {
                errors.add("Merchant ID must not exceed 100 characters");
            }
        }

        // Validate terminal ID
        if (StringUtils.hasText(payload.getTerminalId())) {
            if (payload.getTerminalId().length() > 50) {
                errors.add("Terminal ID must not exceed 50 characters");
            }
        }
    }

    private static void validateChannelSpecificRules(String channel, Payload payload, List<String> errors) {
        switch (channel.toUpperCase()) {
            case "POS":
            case "ATM":
                validateCardTransaction(payload, errors);
                break;
            case "UPI":
                validateUpiTransaction(payload, errors);
                break;
            case "BBPS":
                validateBbpsTransaction(payload, errors);
                break;
            case "PG":
                validatePgTransaction(payload, errors);
                break;
        }
    }

    private static void validateCardTransaction(Payload payload, List<String> errors) {
        if (!StringUtils.hasText(payload.getCardNumber())) {
            errors.add("Card number is required for card transactions");
        }
        if (payload.getAmount() == null) {
            errors.add("Amount is required for card transactions");
        }
        if (!StringUtils.hasText(payload.getMerchantId())) {
            errors.add("Merchant ID is required for card transactions");
        }
        if (!StringUtils.hasText(payload.getTerminalId())) {
            errors.add("Terminal ID is required for card transactions");
        }
    }

    private static void validateUpiTransaction(Payload payload, List<String> errors) {
        if (payload.getAmount() == null) {
            errors.add("Amount is required for UPI transactions");
        }
        // Add UPI-specific validations
    }

    private static void validateBbpsTransaction(Payload payload, List<String> errors) {
        if (!StringUtils.hasText(payload.getAccountNumber())) {
            errors.add("Account number is required for BBPS transactions");
        }
        // Add BBPS-specific validations
    }

    private static void validatePgTransaction(Payload payload, List<String> errors) {
        if (payload.getAmount() == null) {
            errors.add("Amount is required for PG transactions");
        }
        if (!StringUtils.hasText(payload.getMerchantId())) {
            errors.add("Merchant ID is required for PG transactions");
        }
    }

    private static boolean isValidChannel(String channel) {
        return channel != null &&
                ("POS".equals(channel.toUpperCase()) ||
                        "ATM".equals(channel.toUpperCase()) ||
                        "UPI".equals(channel.toUpperCase()) ||
                        "BBPS".equals(channel.toUpperCase()) ||
                        "PG".equals(channel.toUpperCase()));
    }

    // Luhn algorithm for card number validation
    private static boolean isValidLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(cardNumber.substring(i, i + 1));

            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }

            sum += n;
            alternate = !alternate;
        }

        return (sum % 10 == 0);
    }
}
