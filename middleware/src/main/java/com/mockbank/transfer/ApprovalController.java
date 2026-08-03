package com.mockbank.transfer;

import com.mockbank.common.CustomerContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The back-office side of a large transfer. Still behind the fake login — there
 * are no roles in this app — but conceptually this is the approver, not the
 * customer.
 */
@RestController
public class ApprovalController {

    private final CustomerContext customerContext;
    private final ApprovalService approvalService;

    public ApprovalController(CustomerContext customerContext, ApprovalService approvalService) {
        this.customerContext = customerContext;
        this.approvalService = approvalService;
    }

    @GetMapping("/approvals")
    public List<ApprovalResponse> list(@RequestParam(required = false) String status) {
        customerContext.requireCustomerId();
        return approvalService.list(status);
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public ApprovalResponse approve(@PathVariable Long approvalId) {
        customerContext.requireCustomerId();
        return approvalService.approve(approvalId);
    }

    @PostMapping("/approvals/{approvalId}/reject")
    public ApprovalResponse reject(@PathVariable Long approvalId) {
        customerContext.requireCustomerId();
        return approvalService.reject(approvalId);
    }
}
