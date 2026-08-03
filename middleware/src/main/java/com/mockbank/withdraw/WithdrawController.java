package com.mockbank.withdraw;

import com.mockbank.common.CustomerContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
public class WithdrawController {

    private final CustomerContext customerContext;
    private final WithdrawService withdrawService;

    public WithdrawController(CustomerContext customerContext, WithdrawService withdrawService) {
        this.customerContext = customerContext;
        this.withdrawService = withdrawService;
    }

    @PostMapping("/withdraw")
    public WithdrawResponse withdraw(@Valid @RequestBody WithdrawRequest body) {
        return withdrawService.withdraw(
                customerContext.requireCustomerId(), body.account(), body.amount());
    }

    public record WithdrawRequest(
            @NotNull(message = "is required") Long account,
            @NotNull(message = "is required") @Positive(message = "must be greater than zero") BigDecimal amount) {
    }
}
