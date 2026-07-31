package com.mockbank.common;

/**
 * The addressed resource does not exist. Surfaces as {@code 404 {reason, message}}
 * with a specific reason code (CUSTOMER_NOT_FOUND, ACCOUNT_NOT_FOUND, ...).
 */
public class NotFoundException extends RuntimeException {

    private final String reason;

    public NotFoundException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
