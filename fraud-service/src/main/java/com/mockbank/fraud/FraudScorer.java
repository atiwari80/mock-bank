package com.mockbank.fraud;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * INTERNAL. The weighting behind the score lives here and nowhere else — not in
 * the README, not in CLAUDE.md, not in any API response beyond the number
 * itself. Callers get a score and a decision and are expected to mock this
 * service rather than reproduce it.
 */
class FraudScorer {

    private static final int CEILING = 1000;

    private static final int REVIEW_AT = 300;
    private static final int DECLINE_ABOVE = 700;

    private static final BigDecimal AMOUNT_DIVISOR = new BigDecimal("50");
    private static final int AMOUNT_CAP = 500;
    private static final int UNFAMILIAR_DESTINATION = 180;
    private static final int IP_WEIGHT = 3;
    private static final int VELOCITY_WEIGHT = 90;

    FraudCheckController.FraudCheckResponse score(FraudCheckController.FraudCheckRequest request) {
        int total = 0;

        total += amountComponent(request.amount());

        if (Boolean.TRUE.equals(request.recipientIsNew())) {
            total += UNFAMILIAR_DESTINATION;
        }

        total += clamp(orZero(request.ipRisk()), 0, 100) * IP_WEIGHT;
        total += Math.max(orZero(request.recentTransferCount()), 0) * VELOCITY_WEIGHT;

        int score = clamp(total, 0, CEILING);
        return new FraudCheckController.FraudCheckResponse(score, decisionFor(score));
    }

    private int amountComponent(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        int scaled = amount.divide(AMOUNT_DIVISOR, 0, RoundingMode.DOWN).intValue();
        return Math.min(scaled, AMOUNT_CAP);
    }

    private String decisionFor(int score) {
        if (score > DECLINE_ABOVE) {
            return "decline";
        }
        if (score >= REVIEW_AT) {
            return "review";
        }
        return "approve";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
