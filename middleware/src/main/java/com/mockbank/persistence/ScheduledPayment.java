package com.mockbank.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * scheduled_payments(id, account_id, payee, amount, fire_date,
 * status['scheduled'|'pending'|'paid'|'failed'])
 */
@Entity
@Table(name = "scheduled_payments")
public class ScheduledPayment {

    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "payee", nullable = false, length = 120)
    private String payee;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "fire_date", nullable = false)
    private LocalDate fireDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected ScheduledPayment() {
    }

    public ScheduledPayment(Long accountId, String payee, BigDecimal amount, LocalDate fireDate, String status) {
        this.accountId = accountId;
        this.payee = payee;
        this.amount = amount;
        this.fireDate = fireDate;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getPayee() {
        return payee;
    }

    public void setPayee(String payee) {
        this.payee = payee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getFireDate() {
        return fireDate;
    }

    public void setFireDate(LocalDate fireDate) {
        this.fireDate = fireDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
