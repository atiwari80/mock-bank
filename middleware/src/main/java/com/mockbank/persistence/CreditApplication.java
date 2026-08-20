package com.mockbank.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** credit_applications(id, customer_id, ssn_hash, requested_limit, approved_limit, status, bureau_score, created_at) */
@Entity
@Table(name = "credit_applications")
public class CreditApplication {

    public static final String STATUS_PENDING  = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_DECLINED = "declined";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "ssn_hash", nullable = false, length = 64)
    private String ssnHash;

    @Column(name = "requested_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal requestedLimit;

    @Column(name = "approved_limit", precision = 14, scale = 2)
    private BigDecimal approvedLimit;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "bureau_score")
    private Integer bureauScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CreditApplication() {}

    public CreditApplication(Long customerId, String ssnHash, BigDecimal requestedLimit,
                              BigDecimal approvedLimit, String status, Integer bureauScore,
                              LocalDateTime createdAt) {
        this.customerId = customerId;
        this.ssnHash = ssnHash;
        this.requestedLimit = requestedLimit;
        this.approvedLimit = approvedLimit;
        this.status = status;
        this.bureauScore = bureauScore;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public String getSsnHash() { return ssnHash; }
    public BigDecimal getRequestedLimit() { return requestedLimit; }
    public BigDecimal getApprovedLimit() { return approvedLimit; }
    public String getStatus() { return status; }
    public Integer getBureauScore() { return bureauScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
