package com.concerto.omnichannel.service;

import com.concerto.omnichannel.controller.BBPSParsingException;
import com.concerto.omnichannel.dto.Payload;
import com.concerto.omnichannel.dto.TransactionRequest;
import com.concerto.omnichannel.entity.MerchantInfo;
import com.concerto.omnichannel.models.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class BBPSXmlParserService {

    @Autowired
    private MerchantService merchantService;

    @Autowired
    private BBPSConfigurationService configService;

    @Autowired
    private IdGenerationService idGenerationService;

    private static final JAXBContext jaxbContext;
    private static final String NAMESPACE = "http://bbps.org/schema";

    static {
        try {
            jaxbContext = JAXBContext.newInstance(
                    BillFetchRequest.class,
                    BillPaymentRequest.class,
                    TxnStatusRequest.class,
                    BillValidationRequest.class
            );
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to initialize JAXB context", e);
        }
    }

    /**
     * Main parsing method - converts JSON to BBPS XML
     */
    public String parseToXML(TransactionRequest request) {
        try {
            switch (request.getOperation().toLowerCase()) {
                case "fetchbill":
                    return parseToFetchBillXML(request);
                case "pay":
                    return parseToPaymentXML(request);
                case "checkstatus":
                    return parseToStatusCheckXML(request);
                case "validation":
                    return parseToValidationXML(request);
                default:
                    throw new UnsupportedOperationException(
                            "Unsupported BBPS operation: " + request.getOperation());
            }
        } catch (Exception e) {
            throw new BBPSParsingException("Failed to parse BBPS request", e);
        }
    }

    private String parseToValidationXML(TransactionRequest request) {
        return "";
    }

    /**
     * Parse fetchBill JSON to BBPS XML
     */
    private String parseToFetchBillXML(TransactionRequest request) throws JAXBException {
        Optional<MerchantInfo> merchant = merchantService.getMerchantInfo(request.getPayload().getMerchantId());

        BillFetchRequest fetchRequest = new BillFetchRequest();

        // Set Head
        BBPSHead head = new BBPSHead();
        head.setTs(formatTimestamp(System.currentTimeMillis()));
        head.setOrigInst(configService.getOriginalInstitutionId());
        head.setRefId(idGenerationService.generateRefId());
        fetchRequest.setHead(head);

        // Set Analytics
        Analytics analytics = new Analytics();
        analytics.addTag("FETCHREQUESTSTART", formatTimestamp(System.currentTimeMillis()));
        analytics.addTag("FETCHREQUESTEND", formatTimestamp(System.currentTimeMillis()));
        fetchRequest.setAnalytics(analytics);

        // Set Transaction
        Transaction txn = new Transaction();
        txn.setTs(head.getTs());
        txn.setMsgId(idGenerationService.generateMsgId());
        txn.setRiskScores(createDefaultRiskScores());
        fetchRequest.setTransaction(txn);

        // Set Customer
        Customer customer = new Customer();
        customer.setMobile((String) request.getPayload().getAdditionalFields().get("customerMobile"));
        // Add optional customer tags (EMAIL, PAN, AADHAAR) if available
        fetchRequest.setCustomer(customer);

        // Set Agent
        Agent agent = new Agent();
        agent.setId(generateAgentId(merchant.get()));
        agent.setDevice(createDefaultDevice());
        fetchRequest.setAgent(agent);

        // Set Bill Details
        BillDetails billDetails = new BillDetails();
        Biller biller = new Biller();
        biller.setId((String) request.getPayload().getAdditionalFields().get("billerCode"));
        billDetails.setBiller(biller);

        CustomerParams customerParams = new CustomerParams();
        customerParams.addTag("RefFld1", (String) request.getPayload().getAdditionalFields().get("consumerNumber"));
        // Add other reference fields as needed
        billDetails.setCustomerParams(customerParams);

        fetchRequest.setBillDetails(billDetails);

        return marshallToXML(fetchRequest);
    }

    /**
     * Parse payment JSON to BBPS XML
     */
    private String parseToPaymentXML(TransactionRequest request) throws JAXBException {
        Optional<MerchantInfo> merchant = merchantService.getMerchantInfo(request.getPayload().getMerchantId());

        BillPaymentRequest paymentRequest = new BillPaymentRequest();

        // Set Head (similar to fetchBill but different refId generation for payment)
        BBPSHead head = new BBPSHead();
        head.setTs(formatTimestamp(System.currentTimeMillis()));
        head.setOrigInst(configService.getOriginalInstitutionId());
        head.setRefId(idGenerationService.generateRefId());
        paymentRequest.setHead(head);

        // Set Transaction with payment-specific fields
        Transaction txn = new Transaction();
        txn.setTs(head.getTs());
        txn.setMsgId(idGenerationService.generateMsgId());
        txn.setType("FORWARD TYPE REQUEST");
        txn.setTxnReferenceId(idGenerationService.generateTxnRefId());
        txn.setPaymentRefId((String) request.getPayload().getAdditionalFields().get("billerReferenceId"));
        txn.setRiskScores(createDefaultRiskScores());
        paymentRequest.setTransaction(txn);

        // Set Customer, Agent, BillDetails (similar to fetchBill)
        paymentRequest.setCustomer(createCustomer((String) request.getPayload().getAdditionalFields().get("customerMobile")));
        paymentRequest.setAgent(createAgent(merchant));
        paymentRequest.setBillDetails(createBillDetails(request.getPayload()));

        // Set Payment Method
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setQuickPay("No");  // Since we're doing payment after fetch
        paymentMethod.setSplitPay("No");
        paymentMethod.setOFFUSPay("Yes");
        paymentMethod.setPaymentMode(mapPaymentMode((String) request.getPayload().getAdditionalFields().get("paymentMethod")));
        paymentRequest.setPaymentMethod(paymentMethod);

        // Set Amount
        Amount amount = new Amount();
        Amt amt = new Amt();
        amt.setAmount(String.valueOf(request.getPayload().getAmount().multiply(new BigDecimal(100)).longValue())); // Convert to paise
        amt.setCustConvFee("0"); // Set based on configuration
        amt.setCurrency("356"); // INR
        amount.setAmt(amt);
        paymentRequest.setAmount(amount);

        // Set Payment Information (not sent to BBPCU to Biller, but required for request)
        PaymentInformation paymentInfo = new PaymentInformation();
        paymentInfo.addTag("Remarks", "Payment for bill: " + request.getPayload().getAdditionalFields().get("consumerNumber"));
        paymentRequest.setPaymentInformation(paymentInfo);

        return marshallToXML(paymentRequest);
    }

    private BillDetails createBillDetails(Payload payload) {
        BillDetails bill = new BillDetails();
        Biller biller = new Biller();
        biller.setId((String) payload.getAdditionalFields().get("billerId"));
        bill.setBiller(biller);
        return bill;
    }

    private Agent createAgent(Optional<MerchantInfo> merchant) {
        Agent agent = new Agent();
        agent.setId(merchant.get().getMerchantId());

        return agent;
    }

    private Customer createCustomer(String customerMobile) {
        Customer customer = new Customer();
        customer.setMobile(customerMobile);
        return customer;
    }

    /**
     * Parse status check JSON to BBPS XML
     */
    private String parseToStatusCheckXML(TransactionRequest request) throws JAXBException {
        TxnStatusRequest statusRequest = new TxnStatusRequest();

        // Set Head
        BBPSHead head = new BBPSHead();
        head.setTs(formatTimestamp(System.currentTimeMillis()));
        head.setOrigInst(configService.getOriginalInstitutionId());
        head.setRefId(idGenerationService.generateRefId());
        statusRequest.setHead(head);

        // Set Transaction
        Transaction txn = new Transaction();
        txn.setTs(head.getTs());
        txn.setXchangeId("401"); // Transaction status check
        statusRequest.setTransaction(txn);

        // Set Status Request
        TxnStatusReq statusReq = new TxnStatusReq();
        statusReq.setMsgId(idGenerationService.generateMsgId());
        statusReq.setTxnReferenceId((String) request.getPayload().getAdditionalFields().get("bbpsTrxId"));
        statusReq.setComplaintType("Transaction");
        statusRequest.setTxnStatusReq(statusReq);

        return marshallToXML(statusRequest);
    }

    // Helper Methods
    // ==========================================

    private String marshallToXML(Object request) throws JAXBException {
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");

        StringWriter writer = new StringWriter();
        marshaller.marshal(request, writer);
        return writer.toString();
    }

    private String formatTimestamp(long timestamp) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
                .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("Asia/Kolkata")));
    }

    private RiskScores createDefaultRiskScores() {
        RiskScores riskScores = new RiskScores();
        riskScores.addScore(configService.getOriginalInstitutionId(), "TXNRISK", "030");
        riskScores.addScore("BBPS", "TXNRISK", "030");
        return riskScores;
    }

    private String generateAgentId(MerchantInfo merchant) {
        // Format: OU01AI34INT001123456 (Customer BBPOU ID + Agent Institution ID + Channel + Random)
        return configService.getOriginalInstitutionId() +
                merchant.getMerchantId() +
                "INT" +
                String.format("%09d", new Random().nextInt(999999999));
    }

    private Device createDefaultDevice() {
        Device device = new Device();
        device.addTag("INITIATING_CHANNEL", "INT");
        device.addTag("IP", "127.0.0.1");
        device.addTag("MAC", "00-0D-60-07-2A-F0");
        return device;
    }

    private String mapPaymentMode(String paymentMethod) {
        Map<String, String> paymentModeMap = Map.of(
                "UPI", "UPI",
                "POS", "Debit_Card",
                "PG", "Internet_Banking",
                "CARD", "Debit_Card",
                "WALLET", "Wallet"
        );
        return paymentModeMap.getOrDefault(paymentMethod, "UPI");
    }
}

