package com.mockbank.creditcard;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.CreditApplication;
import com.mockbank.persistence.CreditApplicationRepository;
import com.mockbank.persistence.Customer;
import com.mockbank.persistence.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
public class CreditCardService {

    // Scores at or below this value are declined. Scores above it are approved.
    private static final int APPROVAL_THRESHOLD = 620;

    private final CustomerRepository customers;
    private final CreditApplicationRepository applications;
    private final CreditCheckClient creditCheckClient;

    public CreditCardService(CustomerRepository customers,
                              CreditApplicationRepository applications,
                              CreditCheckClient creditCheckClient) {
        this.customers = customers;
        this.applications = applications;
        this.creditCheckClient = creditCheckClient;
    }

    @Transactional
    public ApplyResponse apply(Long customerId, String ssn, BigDecimal requestedLimit) {

        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new BusinessException(
                        "CUSTOMER_INACTIVE", "This customer profile cannot apply for a card."));
        if (!customer.isActive()) {
            throw new BusinessException(
                    "CUSTOMER_INACTIVE",
                    "This profile is " + customer.getStatus() + " and cannot apply for a card.");
        }

        if (ssn == null || !ssn.matches("\\d{9}")) {
            throw new BusinessException(
                    "INVALID_SSN",
                    "SSN must be exactly 9 digits.");
        }

        CreditCheckClient.CreditCheckResponse bureau = creditCheckClient.check(ssn, customerId);

        if (bureau.report().bankruptcy()) {
            throw new BusinessException(
                    "FRAUD_DECLINE",
                    "This application could not be processed due to a flag on the bureau report.");
        }

        if (bureau.score() <= APPROVAL_THRESHOLD) {
            applications.save(new CreditApplication(
                    customerId, hashSsn(ssn), requestedLimit,
                    null, CreditApplication.STATUS_DECLINED, bureau.score(), LocalDateTime.now()));
            throw new BusinessException(
                    "CREDIT_DECLINE",
                    "Your credit profile does not meet our current requirements.");
        }

        CreditApplication record = applications.save(new CreditApplication(
                customerId, hashSsn(ssn), requestedLimit,
                requestedLimit, CreditApplication.STATUS_APPROVED, bureau.score(), LocalDateTime.now()));

        return new ApplyResponse(record.getId(), record.getStatus(), record.getApprovedLimit(), bureau.score());
    }

    private static String hashSsn(String ssn) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(ssn.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
