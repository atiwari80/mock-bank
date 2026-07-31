package com.mockbank.common;

/**
 * The one and only error body shape in this app.
 * <p>
 * {@code reason} is a STABLE machine-readable code that tests assert on;
 * {@code message} is human text the UI shows verbatim. Never collapse a failure
 * into a generic reason — every distinct business failure gets its own code.
 */
public class ErrorResponse {

    private String reason;
    private String message;

    public ErrorResponse() {
    }

    public ErrorResponse(String reason, String message) {
        this.reason = reason;
        this.message = message;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
