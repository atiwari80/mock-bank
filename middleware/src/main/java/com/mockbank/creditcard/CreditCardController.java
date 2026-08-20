package com.mockbank.creditcard;

import com.mockbank.common.CustomerContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/credit-card")
public class CreditCardController {

    private final CreditCardService service;
    private final CustomerContext customerContext;

    public CreditCardController(CreditCardService service, CustomerContext customerContext) {
        this.service = service;
        this.customerContext = customerContext;
    }

    @PostMapping("/apply")
    public ResponseEntity<ApplyResponse> apply(@RequestBody ApplyRequest request) {
        Long customerId = customerContext.requireCustomerId();
        return ResponseEntity.ok(service.apply(customerId, request.ssn(), request.requestedLimit()));
    }
}
