package com.mockbank.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One transaction row in the current customer's statement. */
public record TransactionResponse(
        Long id,
        String type,
        BigDecimal amount,
        String status,
        LocalDateTime createdAt
) {
}
