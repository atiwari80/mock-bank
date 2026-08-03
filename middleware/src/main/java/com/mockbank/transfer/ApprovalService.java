package com.mockbank.transfer;

import com.mockbank.common.BusinessException;
import com.mockbank.persistence.Account;
import com.mockbank.persistence.AccountRepository;
import com.mockbank.persistence.Approval;
import com.mockbank.persistence.ApprovalRepository;
import com.mockbank.persistence.Transaction;
import com.mockbank.persistence.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolving a parked transfer.
 * <p>
 * {@code approvals.transfer_ref} holds the id of the pending transaction the
 * transfer created. Approving debits and completes that row; rejecting voids it.
 * An approval can only be decided once.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository approvals;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;

    public ApprovalService(ApprovalRepository approvals,
                           TransactionRepository transactions,
                           AccountRepository accounts) {
        this.approvals = approvals;
        this.transactions = transactions;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<ApprovalResponse> list(String status) {
        List<Approval> found = status == null
                ? approvals.findAll()
                : approvals.findByStatus(status);

        return found.stream().map(ApprovalService::toResponse).toList();
    }

    @Transactional
    public ApprovalResponse approve(Long approvalId) {
        Approval approval = requireUnresolved(approvalId);
        Transaction parked = requireParkedTransfer(approval);
        Account account = requireAccount(parked.getAccountId());

        // The balance can have moved since the transfer was parked.
        if (account.getBalance().compareTo(parked.getAmount()) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_FUNDS",
                    "The account no longer has enough to cover this transfer, so it was left pending.");
        }

        account.setBalance(account.getBalance().subtract(parked.getAmount()));
        accounts.save(account);

        parked.setStatus(Transaction.STATUS_COMPLETED);
        transactions.save(parked);

        approval.setStatus(Approval.STATUS_APPROVED);
        return toResponse(approvals.save(approval));
    }

    @Transactional
    public ApprovalResponse reject(Long approvalId) {
        Approval approval = requireUnresolved(approvalId);
        Transaction parked = requireParkedTransfer(approval);

        parked.setStatus(Transaction.STATUS_FAILED);
        transactions.save(parked);

        approval.setStatus(Approval.STATUS_REJECTED);
        return toResponse(approvals.save(approval));
    }

    private Approval requireUnresolved(Long approvalId) {
        Approval approval = approvals.findById(approvalId)
                .orElseThrow(() -> new BusinessException(
                        "APPROVAL_NOT_FOUND", "No approval " + approvalId + " exists."));

        if (!Approval.STATUS_PENDING.equals(approval.getStatus())) {
            throw new BusinessException(
                    "APPROVAL_ALREADY_RESOLVED",
                    "This approval was already " + approval.getStatus() + ".");
        }
        return approval;
    }

    private Transaction requireParkedTransfer(Approval approval) {
        long transactionId;
        try {
            transactionId = Long.parseLong(approval.getTransferRef());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    "TRANSFER_NOT_FOUND",
                    "Approval " + approval.getId() + " does not point at a transfer.");
        }

        return transactions.findById(transactionId)
                .orElseThrow(() -> new BusinessException(
                        "TRANSFER_NOT_FOUND",
                        "The transfer behind approval " + approval.getId() + " no longer exists."));
    }

    private Account requireAccount(Long accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new BusinessException(
                        "ACCOUNT_NOT_FOUND", "No account " + accountId + " was found."));
    }

    private static ApprovalResponse toResponse(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getTransferRef(),
                approval.getAmount(),
                approval.getStatus(),
                approval.getCreatedAt());
    }
}
