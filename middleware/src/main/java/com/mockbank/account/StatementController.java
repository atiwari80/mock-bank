package com.mockbank.account;

import com.mockbank.common.CustomerContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The read-only account + statement surface, at the paths the POC spec names.
 * <p>
 * {@code /accounts/me} (see {@link AccountController}) is the convenience the UI
 * dashboard uses; these take an explicit account id. Both enforce that the
 * account belongs to the caller.
 */
@RestController
public class StatementController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerContext customerContext;
    private final AccountService accountService;

    public StatementController(CustomerContext customerContext, AccountService accountService) {
        this.customerContext = customerContext;
        this.accountService = accountService;
    }

    @GetMapping("/account/{accountId}")
    public AccountResponse getAccount(@PathVariable Long accountId) {
        return accountService.getAccountById(customerContext.requireCustomerId(), accountId);
    }

    @GetMapping("/transactions/{accountId}")
    public PagedResponse<TransactionResponse> getStatement(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);

        return accountService.getStatement(
                customerContext.requireCustomerId(), accountId, from, to, safePage, safeSize);
    }
}
