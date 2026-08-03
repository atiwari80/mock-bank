package com.mockbank.transfer;

import com.mockbank.common.CustomerContext;
import com.mockbank.persistence.RecipientRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class TransferController {

    private final CustomerContext customerContext;
    private final TransferService transferService;
    private final RecipientRepository recipients;

    public TransferController(CustomerContext customerContext,
                              TransferService transferService,
                              RecipientRepository recipients) {
        this.customerContext = customerContext;
        this.transferService = transferService;
        this.recipients = recipients;
    }

    @PostMapping("/transfer")
    public TransferResponse transfer(
            @Valid @RequestBody TransferRequest body,
            // Lets a caller simulate a riskier origin; the fraud service decides
            // what, if anything, to do with it.
            @RequestHeader(name = "X-Ip-Risk", defaultValue = "0") int ipRisk) {

        return transferService.transfer(
                customerContext.requireCustomerId(),
                body.fromAccount(),
                body.toRecipient(),
                body.amount(),
                ipRisk);
    }

    /** The recipient picker on the transfer screen. */
    @GetMapping("/recipients")
    public List<RecipientResponse> myRecipients() {
        return recipients.findByCustomerId(customerContext.requireCustomerId()).stream()
                .map(recipient -> new RecipientResponse(
                        recipient.getId(), recipient.getName(), recipient.isEnrolled()))
                .toList();
    }

    public record TransferRequest(
            @NotNull(message = "is required") Long fromAccount,
            @NotNull(message = "is required") Long toRecipient,
            @NotNull(message = "is required") @Positive(message = "must be greater than zero") BigDecimal amount) {
    }

    public record RecipientResponse(Long id, String name, boolean enrolled) {
    }
}
