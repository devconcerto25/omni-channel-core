package com.concerto.omnichannel.service;

import com.concerto.omnichannel.controller.BBPSParsingException;
import com.concerto.omnichannel.dto.TransactionResponse;
import com.concerto.omnichannel.models.BillFetchResponse;
import com.concerto.omnichannel.models.BillPaymentResponse;
import com.concerto.omnichannel.models.BillerResponse;
import com.concerto.omnichannel.models.TxnStatusResponse;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class BBPSResponseParserService {

    private static final JAXBContext jaxbContext;

    static {
        try {
            jaxbContext = JAXBContext.newInstance(
                    BillFetchResponse.class,
                    BillPaymentResponse.class,
                    TxnStatusResponse.class
            );
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to initialize JAXB context", e);
        }
    }

    /**
     * Parse BBPS XML response to JSON
     */
    public TransactionResponse parseFromXML(String xmlResponse, String operationType) {
        try {
            switch (operationType.toLowerCase()) {
                case "fetchbill":
                    return parseFetchBillResponse(xmlResponse);
                case "pay":
                    return parsePaymentResponse(xmlResponse);
                case "checkstatus":
                    return parseStatusResponse(xmlResponse);
                default:
                    throw new UnsupportedOperationException("Unsupported operation: " + operationType);
            }
        } catch (Exception e) {
            throw new BBPSParsingException("Failed to parse BBPS response", e);
        }
    }

    private TransactionResponse parseStatusResponse(String xmlResponse) {
       return  new TransactionResponse();
    }

    private TransactionResponse parseFetchBillResponse(String xmlResponse) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        BillFetchResponse response = (BillFetchResponse) unmarshaller.unmarshal(
                new StringReader(xmlResponse));

        TransactionResponse builder = new TransactionResponse();
        builder.setSuccess("000".equals(response.getReason().getResponseCode()));
      //  builder.setStatus(mapResponseStatus(response.getReason().getResponseCode()));
        builder.setCorrelationId(response.getHead().getRefId());
        builder.setResponseTime(parseTimestamp(response.getHead().getTs()));

        if (response.getBillerResponse() != null) {
            BillerResponse billerResp = response.getBillerResponse();
            builder.data(Map.of(
                    "customerName", billerResp.getCustomerName(),
                    "amount", new BigDecimal(billerResp.getAmount()).divide(new BigDecimal(100)), // Convert from paise
                    "dueDate", billerResp.getDueDate(),
                    "billNumber", billerResp.getBillNumber(),
                    "billPeriod", billerResp.getBillPeriod(),
                    "billReferenceId", response.getHead().getRefId() // Use for payment
            ));
        }

        if (!builder.build().isSuccess()) {
            builder.message(response.getReason().getComplianceReason());
        }

        return builder.build();
    }

    private TransactionResponse parsePaymentResponse(String xmlResponse) throws JAXBException {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        BillPaymentResponse response = (BillPaymentResponse) unmarshaller.unmarshal(
                new StringReader(xmlResponse));

        return TransactionResponse.builder()
                .success("000".equals(response.getReason().getResponseCode()))
                .status(mapResponseStatus(response.getReason().getResponseCode()))
                .correlationId(response.getHead().getRefId())
                .timestamp(parseTimestamp(response.getHead().getTs()))
                .data(Map.of(
                        "transactionReferenceId", response.getTxn().getTxnReferenceId(),
                        "approvalRefNum", response.getReason().getApprovalRefNum(),
                        "billNumber", response.getBillerResponse() != null ? response.getBillerResponse().getBillNumber() : "",
                        "amount", response.getBillerResponse() != null ?
                                new BigDecimal(response.getBillerResponse().getAmount()).divide(new BigDecimal(100)) :
                                BigDecimal.ZERO
                ))
                .message(response.getReason().getResponseReason())
                .build();
    }

    // Helper methods
    private String mapResponseStatus(String responseCode) {
        return "000".equals(responseCode) ? "SUCCESS" : "FAILED";
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }
}
