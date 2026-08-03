package com.mockbank.account;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.Account;
import com.mockbank.persistence.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountForCustomer(Long customerId) {
        Account account = accounts.findFirstByCustomerIdOrderByIdAsc(customerId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "No account was found for this customer."
                ));

        return new AccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getBalance(),
                account.isHold(),
                account.getDailyWithdrawn(),
                account.getStatus()
        );
    }
}
