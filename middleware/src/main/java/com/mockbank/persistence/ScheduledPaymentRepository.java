package com.mockbank.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {

    List<ScheduledPayment> findByAccountId(Long accountId);

    List<ScheduledPayment> findByAccountIdAndStatus(Long accountId, String status);
}
