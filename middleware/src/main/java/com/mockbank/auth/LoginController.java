package com.mockbank.auth;

import com.mockbank.common.NotFoundException;
import com.mockbank.persistence.Customer;
import com.mockbank.persistence.CustomerRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fake login. No password, no session, no token — POST an id, get the customer
 * back, and from then on the UI sends {@code X-Customer-Id} on every request.
 */
@RestController
public class LoginController {

    private final CustomerRepository customers;

    public LoginController(CustomerRepository customers) {
        this.customers = customers;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        Customer customer = customers.findById(body.customerId())
                .orElseThrow(() -> new NotFoundException("CUSTOMER_NOT_FOUND",
                        "No customer with id " + body.customerId() + "."));

        return ResponseEntity.ok(new LoginResponse(customer.getId(), customer.getName()));
    }

    public record LoginRequest(@NotNull(message = "is required") Long customerId) {
    }

    public record LoginResponse(Long customerId, String name) {
    }
}
