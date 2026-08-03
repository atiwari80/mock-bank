package com.mockbank.account;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.Account;
import com.mockbank.persistence.AccountRepository;
import com.mockbank.persistence.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public AccountService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountForCustomer(Long customerId) {
        Account account = requireAccount(customerId);

        return new AccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getBalance(),
                account.isHold(),
                account.getDailyWithdrawn(),
                account.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsForCustomer(Long customerId) {
        Account account = requireAccount(customerId);

        return transactions.findByAccountIdOrderByCreatedAtDesc(account.getId()).stream()
                .map(transaction -> new TransactionResponse(
                        transaction.getId(),
                        transaction.getType(),
                        transaction.getAmount(),
                        transaction.getStatus(),
                        transaction.getCreatedAt()
                ))
                .toList();
    }

    private Account requireAccount(Long customerId) {
        return accounts.findFirstByCustomerIdOrderByIdAsc(customerId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "No account was found for this customer."
                ));
    }
}
