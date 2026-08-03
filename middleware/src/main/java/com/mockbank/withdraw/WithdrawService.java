package com.mockbank.withdraw;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.Account;
import com.mockbank.persistence.AccountRepository;
import com.mockbank.persistence.Transaction;
import com.mockbank.persistence.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cash out of your own account.
 * <p>
 * The check order below IS the contract: the first failure is the reason that
 * comes back, so a request that breaks several rules at once is still
 * deterministic. Both caps are inclusive — exactly $2,000 is allowed.
 * <p>
 * A hold or a frozen customer does NOT block a withdrawal; those only stop
 * transfers.
 */
@Service
public class WithdrawService {

    static final BigDecimal PER_TRANSACTION_LIMIT = new BigDecimal("2000.00");
    static final BigDecimal DAILY_LIMIT = new BigDecimal("2000.00");

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public WithdrawService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public WithdrawResponse withdraw(Long customerId, Long accountId, BigDecimal amount) {
        // 1. the account has to exist and be yours
        Account account = accounts.findById(accountId)
                .filter(candidate -> candidate.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "No account " + accountId + " was found for this customer."));

        // 2. you have to have the money
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_FUNDS",
                    "Your available balance is " + money(account.getBalance()) + ".");
        }

        // 3. no single withdrawal above the per-transaction cap
        if (amount.compareTo(PER_TRANSACTION_LIMIT) > 0) {
            throw new BusinessException(
                    "EXCEEDS_TXN_LIMIT",
                    "A single withdrawal cannot exceed " + money(PER_TRANSACTION_LIMIT) + ".");
        }

        // 4. and not past the running daily total either
        BigDecimal wouldBeToday = account.getDailyWithdrawn().add(amount);
        if (wouldBeToday.compareTo(DAILY_LIMIT) > 0) {
            BigDecimal remaining = DAILY_LIMIT.subtract(account.getDailyWithdrawn()).max(BigDecimal.ZERO);
            throw new BusinessException(
                    "EXCEEDS_DAILY_LIMIT",
                    "This would take today's withdrawals to " + money(wouldBeToday) + ", over the "
                            + money(DAILY_LIMIT) + " daily limit. You can still withdraw "
                            + money(remaining) + " today.");
        }

        account.setBalance(account.getBalance().subtract(amount));
        account.setDailyWithdrawn(wouldBeToday);
        accounts.save(account);

        Transaction record = transactions.save(new Transaction(
                account.getId(),
                Transaction.TYPE_WITHDRAW,
                amount,
                Transaction.STATUS_COMPLETED,
                LocalDateTime.now()));

        return new WithdrawResponse(
                record.getId(),
                account.getBalance(),
                account.getDailyWithdrawn());
    }

    private static String money(BigDecimal amount) {
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
