package com.mockbank.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {

    List<ScheduledPayment> findByAccountId(Long accountId);

    List<ScheduledPayment> findByAccountIdAndStatus(Long accountId, String status);

    List<ScheduledPayment> findByAccountIdOrderByFireDateAsc(Long accountId);

    /** Everything due on or before a date that has not reached a terminal state. */
    List<ScheduledPayment> findByStatusInAndFireDateLessThanEqual(
            java.util.Collection<String> statuses, java.time.LocalDate asOf);
}
