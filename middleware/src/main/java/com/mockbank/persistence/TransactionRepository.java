package com.mockbank.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<Transaction> findByAccountIdAndTypeAndCreatedAtAfter(Long accountId, String type, LocalDateTime after);

    long countByAccountIdAndTypeAndCreatedAtAfter(Long accountId, String type, LocalDateTime after);
}
