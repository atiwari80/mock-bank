package com.mockbank.transfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ApprovalResponse(
        Long id,
        String transferRef,
        BigDecimal amount,
        String status,
        LocalDateTime createdAt) {
}
