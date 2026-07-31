package com.mockbank.common;

import com.mockbank.persistence.Customer;
import com.mockbank.persistence.CustomerRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * "Who is calling?" — the whole of authentication in this app.
 * <p>
 * Fake login: the UI sends {@code X-Customer-Id: <id>} on every request. Missing,
 * non-numeric, or unknown id → 401 NOT_AUTHENTICATED. Feature controllers just
 * inject this bean and call {@link #requireCustomerId()}.
 * <p>
 * Note this deliberately says nothing about whether the customer is active —
 * a frozen customer authenticates fine and is refused later by feature rules
 * with their own reason code.
 */
@Component
public class CustomerContext {

    public static final String CUSTOMER_ID_HEADER = "X-Customer-Id";

    private final HttpServletRequest request;
    private final CustomerRepository customers;

    public CustomerContext(HttpServletRequest request, CustomerRepository customers) {
        this.request = request;
        this.customers = customers;
    }

    /** The current customer id, verified to exist. */
    public Long requireCustomerId() {
        return requireCustomer().getId();
    }

    /** The current customer record. */
    public Customer requireCustomer() {
        String raw = request.getHeader(CUSTOMER_ID_HEADER);
        if (raw == null || raw.isBlank()) {
            throw new NotAuthenticatedException("Missing " + CUSTOMER_ID_HEADER + " header.");
        }

        long id;
        try {
            id = Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw new NotAuthenticatedException(CUSTOMER_ID_HEADER + " is not a valid customer id.");
        }

        return customers.findById(id)
                .orElseThrow(() -> new NotAuthenticatedException("Unknown customer id " + raw + "."));
    }
}
