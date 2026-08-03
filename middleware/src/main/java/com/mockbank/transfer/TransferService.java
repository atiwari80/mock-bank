package com.mockbank.transfer;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.Account;
import com.mockbank.persistence.AccountRepository;
import com.mockbank.persistence.Approval;
import com.mockbank.persistence.ApprovalRepository;
import com.mockbank.persistence.Customer;
import com.mockbank.persistence.CustomerRepository;
import com.mockbank.persistence.Recipient;
import com.mockbank.persistence.RecipientRepository;
import com.mockbank.persistence.Transaction;
import com.mockbank.persistence.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sending money out. The checks fan out across the customer record, the account,
 * the fraud-check service and the recipient before anything is debited, and each
 * one that fails says exactly which one it was.
 */
@Service
public class TransferService {

    private static final BigDecimal LARGE_TRANSFER = new BigDecimal("10000.00");
    private static final int ESCALATION_TRIGGER = 2;
    private static final int BUSY_ACCOUNT = 2;

    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final RecipientRepository recipients;
    private final TransactionRepository transactions;
    private final ApprovalRepository approvals;
    private final FraudClient fraudClient;

    public TransferService(CustomerRepository customers,
                           AccountRepository accounts,
                           RecipientRepository recipients,
                           TransactionRepository transactions,
                           ApprovalRepository approvals,
                           FraudClient fraudClient) {
        this.customers = customers;
        this.accounts = accounts;
        this.recipients = recipients;
        this.transactions = transactions;
        this.approvals = approvals;
        this.fraudClient = fraudClient;
    }

    @Transactional
    public TransferResponse transfer(Long customerId, Long fromAccount, Long toRecipient,
                                     BigDecimal amount, int ipRisk) {

        // --- the customer themselves ---
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new BusinessException(
                        "CUSTOMER_INACTIVE", "This customer profile cannot send money."));
        if (!customer.isActive()) {
            throw new BusinessException(
                    "CUSTOMER_INACTIVE",
                    "This profile is " + customer.getStatus() + " and cannot send money.");
        }

        // --- the account it is coming from ---
        Account account = accounts.findById(fromAccount)
                .filter(candidate -> candidate.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND",
                        "No account " + fromAccount + " was found for this customer."));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_FUNDS",
                    "Your available balance is " + money(account.getBalance()) + ".");
        }

        if (account.isHold()) {
            throw new BusinessException(
                    "ACCOUNT_HOLD",
                    "There is a hold on this account, so money cannot leave it right now.");
        }

        // --- the recipient (loaded now, enrolment enforced after scoring) ---
        Recipient recipient = recipients.findById(toRecipient)
                .filter(candidate -> candidate.getCustomerId().equals(customerId))
                .orElseThrow(() -> new BusinessException(
                        "RECIPIENT_NOT_ENROLLED",
                        "That recipient is not set up to receive money from you."));

        // --- the black box ---
        boolean firstTimeDestination = !transactions.existsByAccountIdAndTypeAndStatus(
                account.getId(), Transaction.TYPE_TRANSFER, Transaction.STATUS_COMPLETED);
        long recentTransfers = transactions.countByAccountIdAndTypeAndCreatedAtAfter(
                account.getId(), Transaction.TYPE_TRANSFER, LocalDateTime.now().minusHours(1));

        FraudClient.FraudCheckResponse fraud = fraudClient.check(new FraudClient.FraudCheckRequest(
                account.getId(), amount, recipient.getId(), firstTimeDestination, ipRisk, recentTransfers));

        if (fraud.isDecline()) {
            throw new BusinessException(
                    "FRAUD_DECLINE",
                    "This transfer was declined by our fraud checks.");
        }

        if (!recipient.isEnrolled()) {
            throw new BusinessException(
                    "RECIPIENT_NOT_ENROLLED",
                    "\"" + recipient.getName() + "\" has not completed enrolment yet.");
        }

        if (escalationSignals(amount, firstTimeDestination, fraud, recentTransfers) >= ESCALATION_TRIGGER) {
            return park(account, amount);
        }

        if (fraud.isReview()) {
            throw new BusinessException(
                    "FRAUD_REVIEW",
                    "This transfer has been held for review and was not sent.");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accounts.save(account);

        Transaction record = transactions.save(new Transaction(
                account.getId(), Transaction.TYPE_TRANSFER, amount,
                Transaction.STATUS_COMPLETED, LocalDateTime.now()));

        return TransferResponse.completed(record.getId(), account.getBalance());
    }

    private int escalationSignals(BigDecimal amount, boolean firstTimeDestination,
                                  FraudClient.FraudCheckResponse fraud, long recentTransfers) {
        int signals = 0;
        if (amount.compareTo(LARGE_TRANSFER) > 0) {
            signals += 2;
        }
        if (firstTimeDestination) {
            signals += 1;
        }
        if (fraud.isReview()) {
            signals += 1;
        }
        if (recentTransfers > BUSY_ACCOUNT) {
            signals += 1;
        }
        return signals;
    }

    /**
     * Holds the transfer instead of completing it: the money stays put, the
     * transaction sits pending, and an approval is raised against it.
     */
    private TransferResponse park(Account account, BigDecimal amount) {
        Transaction parked = transactions.save(new Transaction(
                account.getId(), Transaction.TYPE_TRANSFER, amount,
                Transaction.STATUS_PENDING, LocalDateTime.now()));

        Approval approval = approvals.save(new Approval(
                String.valueOf(parked.getId()), amount, Approval.STATUS_PENDING, LocalDateTime.now()));

        return TransferResponse.pendingApproval(parked.getId(), approval.getId(), account.getBalance());
    }

    private static String money(BigDecimal amount) {
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
