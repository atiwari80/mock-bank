package com.mockbank.fraud;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fraud check. Stateless, no database — the caller passes in everything,
 * including velocity, and gets back a score and a decision.
 * <p>
 * Two shapes of the same call: the query-parameter form the spec documents, and
 * a JSON body form. Whichever a caller uses, the answer is identical.
 * <p>
 * How the score is computed is intentionally not documented anywhere the caller
 * can see. Treat this service as a black box.
 */
@RestController
public class FraudCheckController {

    private final FraudScorer scorer = new FraudScorer();

    @GetMapping("/fraud-check")
    public FraudCheckResponse check(
            @RequestParam(name = "account", required = false) Long account,
            @RequestParam(name = "amount") BigDecimal amount,
            @RequestParam(name = "recipient", required = false) Long recipient,
            @RequestParam(name = "ip", required = false, defaultValue = "0") Integer ip,
            // Velocity and recipient history are the caller's to supply — this
            // service remembers nothing between calls.
            @RequestParam(name = "recipientIsNew", required = false, defaultValue = "false") Boolean recipientIsNew,
            @RequestParam(name = "recentTransferCount", required = false, defaultValue = "0") Integer recentTransferCount) {

        return scorer.score(new FraudCheckRequest(
                account, amount, recipient, recipientIsNew, ip, recentTransferCount));
    }

    @PostMapping("/fraud-check")
    public FraudCheckResponse check(@RequestBody FraudCheckRequest request) {
        return scorer.score(request);
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
