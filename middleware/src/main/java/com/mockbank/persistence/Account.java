package com.mockbank.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** accounts(id, customer_id, balance, hold, daily_withdrawn, status) */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(name = "hold", nullable = false)
    private boolean hold;

    @Column(name = "daily_withdrawn", nullable = false, precision = 14, scale = 2)
    private BigDecimal dailyWithdrawn;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected Account() {
    }

    public Account(Long customerId, BigDecimal balance, boolean hold, BigDecimal dailyWithdrawn, String status) {
        this.customerId = customerId;
        this.balance = balance;
        this.hold = hold;
        this.dailyWithdrawn = dailyWithdrawn;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public boolean isHold() {
        return hold;
    }

    public void setHold(boolean hold) {
        this.hold = hold;
    }

    public BigDecimal getDailyWithdrawn() {
        return dailyWithdrawn;
    }

    public void setDailyWithdrawn(BigDecimal dailyWithdrawn) {
        this.dailyWithdrawn = dailyWithdrawn;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
