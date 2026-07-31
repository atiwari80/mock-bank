package com.mockbank.common;

/**
 * No usable {@code X-Customer-Id} on the request. Surfaces as
 * {@code 401 {reason: "NOT_AUTHENTICATED"}}.
 */
public class NotAuthenticatedException extends RuntimeException {

    public static final String REASON = "NOT_AUTHENTICATED";

    public NotAuthenticatedException(String message) {
        super(message);
    }
}
