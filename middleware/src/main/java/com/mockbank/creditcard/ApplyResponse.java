package com.mockbank.creditcard;

import java.math.BigDecimal;

public record ApplyResponse(Long applicationId, String status, BigDecimal approvedLimit, int bureauScore) {}
