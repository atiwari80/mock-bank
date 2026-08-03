package com.mockbank.transfer;

import java.math.BigDecimal;

/**
 * @param status     {@code completed} or {@code pending_approval}
 * @param approvalId set only when the transfer was routed for approval
 */
public record TransferResponse(
        Long transferId,
        String status,
        Long approvalId,
        String transferRef,
        BigDecimal balance) {

    public static TransferResponse completed(Long transferId, BigDecimal balance) {
        return new TransferResponse(transferId, "completed", null, String.valueOf(transferId), balance);
    }

    public static TransferResponse pendingApproval(Long transferId, Long approvalId, BigDecimal balance) {
        return new TransferResponse(transferId, "pending_approval", approvalId, String.valueOf(transferId), balance);
    }
}
