package com.mockbank.auth;

import com.mockbank.common.CustomerContext;
import com.mockbank.persistence.Customer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Who am I?" — the companion to fake login. Resolves the caller from the
 * {@code X-Customer-Id} header, which makes it the one endpoint in the shared
 * layer that exercises the 401 NOT_AUTHENTICATED contract.
 */
@RestController
public class SessionController {

    private final CustomerContext customerContext;

    public SessionController(CustomerContext customerContext) {
        this.customerContext = customerContext;
    }

    @GetMapping("/whoami")
    public WhoAmIResponse whoAmI() {
        Customer customer = customerContext.requireCustomer();
        return new WhoAmIResponse(customer.getId(), customer.getName(), customer.getStatus());
    }

    public record WhoAmIResponse(Long customerId, String name, String status) {
    }
}
