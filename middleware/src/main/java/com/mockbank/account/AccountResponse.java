package com.mockbank.account;

import java.math.BigDecimal;

/** The current customer's account summary displayed by the dashboard. */
public record AccountResponse(
        Long id,
        Long customerId,
        BigDecimal balance,
        boolean hold,
        BigDecimal dailyWithdrawn,
        String status
) {
}
