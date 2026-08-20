package com.mockbank.creditcheck;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bureau-style credit check. Stateless, no database — the caller passes in the
 * applicant identifiers and receives a score and a report fragment.
 * <p>
 * How the score is derived is intentionally not documented anywhere the caller
 * can see. Treat this service as a black box.
 */
@RestController
public class CreditCheckController {

    private final CreditScorer scorer = new CreditScorer();

    @GetMapping("/credit-check")
    public CreditCheckResponse check(
            @RequestParam String ssn,
            @RequestParam(required = false) Long customerId) {
        return scorer.score(ssn, customerId);
    }

    public record BureauReport(int openAccounts, int latePayments, boolean bankruptcy) {}

    public record CreditCheckResponse(int score, BureauReport report) {}
}
