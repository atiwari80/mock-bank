package com.mockbank.billpay;

import com.mockbank.common.CustomerContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class BillPayController {

    private final CustomerContext customerContext;
    private final BillPayService billPayService;

    public BillPayController(CustomerContext customerContext, BillPayService billPayService) {
        this.customerContext = customerContext;
        this.billPayService = billPayService;
    }

    @PostMapping("/schedule-payment")
    public ScheduledPaymentResponse schedule(@Valid @RequestBody SchedulePaymentRequest body) {
        return billPayService.schedule(
                customerContext.requireCustomerId(), body.payee(), body.amount(), body.date());
    }

    @GetMapping("/scheduled-payments")
    public List<ScheduledPaymentResponse> list() {
        return billPayService.list(customerContext.requireCustomerId());
    }

    @PostMapping("/scheduled-payments/{paymentId}/cancel")
    public Map<String, Object> cancel(@PathVariable Long paymentId) {
        billPayService.cancel(customerContext.requireCustomerId(), paymentId);
        return Map.of("cancelled", true, "paymentId", paymentId);
    }

    /**
     * Fires everything due as at a date the caller chooses, one step per call.
     * Deliberately not a background job — tests need to control the clock.
     */
    @PostMapping("/scheduled-payments/run")
    public BillPayService.RunResult run(@RequestBody(required = false) RunRequest body) {
        customerContext.requireCustomerId();
        LocalDate asOf = body == null || body.asOfDate() == null ? LocalDate.now() : body.asOfDate();
        return billPayService.runDue(asOf);
    }

    public record SchedulePaymentRequest(
            @NotBlank(message = "is required") String payee,
            @NotNull(message = "is required") @Positive(message = "must be greater than zero") BigDecimal amount,
            @NotNull(message = "is required") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    }

    public record RunRequest(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
    }
}
