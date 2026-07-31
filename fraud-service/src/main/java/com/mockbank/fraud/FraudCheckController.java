package com.mockbank.fraud;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fraud check. Stateless, no database — the caller passes in everything,
 * including velocity, and gets back a score and a decision.
 * <p>
 * How the score is computed is intentionally not documented anywhere the caller
 * can see. Treat this service as a black box.
 */
@RestController
public class FraudCheckController {

    @PostMapping("/fraud-check")
    public FraudCheckResponse check(@RequestBody FraudCheckRequest request) {
        // TODO: Person A implements opaque scoring.
        return new FraudCheckResponse(0, "approve");
    }

    public record FraudCheckRequest(
            Long accountId,
            BigDecimal amount,
            Long recipientId,
            Boolean recipientIsNew,
            Integer ipRisk,
            Integer recentTransferCount) {
    }

    public record FraudCheckResponse(int score, String decision) {
    }
}
