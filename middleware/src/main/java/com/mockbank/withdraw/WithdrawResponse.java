package com.mockbank.withdraw;

import java.math.BigDecimal;

public record WithdrawResponse(
        Long transactionId,
        BigDecimal balance,
        BigDecimal dailyWithdrawn) {
}
