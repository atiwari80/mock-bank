package com.mockbank.common;

/**
 * Thrown by a feature service when a request is well-formed but the business
 * rules refuse it. Always surfaces as {@code 422 {reason, message}}.
 * <p>
 * The reason code must be specific (INSUFFICIENT_FUNDS, ACCOUNT_HOLD,
 * RECIPIENT_NOT_ENROLLED, ...). "DENIED" or "FAILED" is never acceptable.
 */
public class BusinessException extends RuntimeException {

    private final String reason;

    public BusinessException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
