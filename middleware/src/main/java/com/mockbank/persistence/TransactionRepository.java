package com.mockbank.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    /** Statement view: date-range filtered and paged. */
    Page<Transaction> findByAccountIdAndCreatedAtBetween(
            Long accountId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    boolean existsByAccountIdAndTypeAndStatus(Long accountId, String type, String status);

    List<Transaction> findByAccountIdAndTypeAndCreatedAtAfter(Long accountId, String type, LocalDateTime after);

    long countByAccountIdAndTypeAndCreatedAtAfter(Long accountId, String type, LocalDateTime after);
}
