package com.mockbank.creditcard;

import java.math.BigDecimal;

public record ApplyRequest(Long customerId, String ssn, BigDecimal requestedLimit) {}
