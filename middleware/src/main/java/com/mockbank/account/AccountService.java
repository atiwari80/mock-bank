package com.mockbank.account;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.Account;
import com.mockbank.persistence.AccountRepository;
import com.mockbank.persistence.Transaction;
import com.mockbank.persistence.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountService {

    // Open-ended bounds so an absent from/to still uses one range query.
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime FOREVER = LocalDateTime.of(2999, 12, 31, 23, 59, 59);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public AccountService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountForCustomer(Long customerId) {
        return toResponse(requireAccount(customerId));
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long customerId, Long accountId) {
        return toResponse(requireOwnedAccount(customerId, accountId));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsForCustomer(Long customerId) {
        Account account = requireAccount(customerId);

        return transactions.findByAccountIdOrderByCreatedAtDesc(account.getId()).stream()
                .map(AccountService::toResponse)
                .toList();
    }

    /** Statement: newest first, optional inclusive date range, paged. */
    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> getStatement(
            Long customerId, Long accountId, LocalDate from, LocalDate to, int page, int size) {

        Account account = requireOwnedAccount(customerId, accountId);

        LocalDateTime start = from == null ? BEGINNING : from.atStartOfDay();
        LocalDateTime end = to == null ? FOREVER : to.atTime(23, 59, 59);

        Page<Transaction> found = transactions.findByAccountIdAndCreatedAtBetween(
                account.getId(), start, end,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PagedResponse<>(
                found.getContent().stream().map(AccountService::toResponse).toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages());
    }

    private Account requireAccount(Long customerId) {
        return accounts.findFirstByCustomerIdOrderByIdAsc(customerId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "No account was found for this customer."
                ));
    }

    /**
     * Someone else's account is reported as not found rather than forbidden — the
     * error contract has no code for "exists but isn't yours", and saying so would
     * confirm the account exists.
     */
    private Account requireOwnedAccount(Long customerId, Long accountId) {
        return accounts.findById(accountId)
                .filter(account -> account.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "No account " + accountId + " was found for this customer."
                ));
    }

    private static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCustomerId(),
                account.getBalance(),
                account.isHold(),
                account.getDailyWithdrawn(),
                account.getStatus()
        );
    }

    private static TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getCreatedAt()
        );
    }
}
