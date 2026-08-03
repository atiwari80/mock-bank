package com.mockbank.billpay;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ScheduledPaymentResponse(
        Long id,
        String payee,
        BigDecimal amount,
        LocalDate fireDate,
        String status) {
}
