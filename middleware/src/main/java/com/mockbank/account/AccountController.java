package com.mockbank.account;

import com.mockbank.common.CustomerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final CustomerContext customerContext;
    private final AccountService accountService;

    public AccountController(CustomerContext customerContext, AccountService accountService) {
        this.customerContext = customerContext;
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public AccountResponse getMyAccount() {
        Long customerId = customerContext.requireCustomerId();
        return accountService.getAccountForCustomer(customerId);
    }
}
