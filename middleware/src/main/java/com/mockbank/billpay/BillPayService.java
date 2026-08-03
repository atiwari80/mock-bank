package com.mockbank.billpay;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.Account;
import com.mockbank.persistence.AccountRepository;
import com.mockbank.persistence.ScheduledPayment;
import com.mockbank.persistence.ScheduledPaymentRepository;
import com.mockbank.persistence.Transaction;
import com.mockbank.persistence.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled payments: {@code scheduled → pending → paid | failed}.
 * <p>
 * Nothing here runs on a timer. {@link #runDue(LocalDate)} advances every due
 * payment exactly ONE step, so a test can drive the clock itself and still
 * observe the intermediate {@code pending} state.
 */
@Service
public class BillPayService {

    private final ScheduledPaymentRepository payments;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public BillPayService(ScheduledPaymentRepository payments,
                          AccountRepository accounts,
                          TransactionRepository transactions) {
        this.payments = payments;
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional
    public ScheduledPaymentResponse schedule(Long customerId, String payee, BigDecimal amount, LocalDate fireDate) {
        Account account = requireAccount(customerId);

        ScheduledPayment saved = payments.save(new ScheduledPayment(
                account.getId(), payee, amount, fireDate, ScheduledPayment.STATUS_SCHEDULED));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ScheduledPaymentResponse> list(Long customerId) {
        Account account = requireAccount(customerId);

        return payments.findByAccountIdOrderByFireDateAsc(account.getId()).stream()
                .map(BillPayService::toResponse)
                .toList();
    }

    /** Only a payment that has not started moving can be called off. */
    @Transactional
    public void cancel(Long customerId, Long paymentId) {
        Account account = requireAccount(customerId);

        ScheduledPayment payment = payments.findById(paymentId)
                .filter(candidate -> candidate.getAccountId().equals(account.getId()))
                .orElseThrow(() -> new BusinessException(
                        "PAYMENT_NOT_FOUND", "No scheduled payment " + paymentId + " on this account."));

        if (!ScheduledPayment.STATUS_SCHEDULED.equals(payment.getStatus())) {
            throw new BusinessException(
                    "NOT_CANCELLABLE",
                    "This payment is already " + payment.getStatus() + " and can no longer be cancelled.");
        }

        // The status column has no 'cancelled' value and the schema is frozen,
        // so cancelling removes the row.
        payments.delete(payment);
    }

    /**
     * Advances every payment due on or before {@code asOf} by one step:
     * {@code scheduled → pending}, then on a later call {@code pending → paid}
     * (or {@code failed} when the money is not there on the day).
     */
    @Transactional
    public RunResult runDue(LocalDate asOf) {
        List<ScheduledPayment> due = payments.findByStatusInAndFireDateLessThanEqual(
                List.of(ScheduledPayment.STATUS_SCHEDULED, ScheduledPayment.STATUS_PENDING), asOf);

        int queued = 0;
        int paid = 0;
        int failed = 0;

        for (ScheduledPayment payment : due) {
            if (ScheduledPayment.STATUS_SCHEDULED.equals(payment.getStatus())) {
                payment.setStatus(ScheduledPayment.STATUS_PENDING);
                payments.save(payment);
                queued++;
                continue;
            }

            Account account = accounts.findById(payment.getAccountId()).orElse(null);
            if (account == null || account.getBalance().compareTo(payment.getAmount()) < 0) {
                payment.setStatus(ScheduledPayment.STATUS_FAILED);
                payments.save(payment);
                transactions.save(new Transaction(
                        payment.getAccountId(), Transaction.TYPE_BILLPAY, payment.getAmount(),
                        Transaction.STATUS_FAILED, LocalDateTime.now()));
                failed++;
                continue;
            }

            account.setBalance(account.getBalance().subtract(payment.getAmount()));
            accounts.save(account);

            payment.setStatus(ScheduledPayment.STATUS_PAID);
            payments.save(payment);
            transactions.save(new Transaction(
                    payment.getAccountId(), Transaction.TYPE_BILLPAY, payment.getAmount(),
                    Transaction.STATUS_COMPLETED, LocalDateTime.now()));
            paid++;
        }

        return new RunResult(asOf, queued, paid, failed);
    }

    private Account requireAccount(Long customerId) {
        return accounts.findFirstByCustomerIdOrderByIdAsc(customerId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND", "No account was found for this customer."));
    }

    private static ScheduledPaymentResponse toResponse(ScheduledPayment payment) {
        return new ScheduledPaymentResponse(
                payment.getId(),
                payment.getPayee(),
                payment.getAmount(),
                payment.getFireDate(),
                payment.getStatus());
    }

    public record RunResult(LocalDate asOf, int queued, int paid, int failed) {
    }
}
