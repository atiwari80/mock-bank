package com.mockbank.creditcheck;

import java.util.HashMap;
import java.util.Map;

/**
 * INTERNAL. The bureau records live here and nowhere else — not in the README,
 * not in CLAUDE.md, not in any API response beyond the numbers themselves.
 * Callers are expected to mock this service rather than reproduce its records.
 */
class CreditScorer {

    private static final Map<String, SeedRecord> RECORDS = new HashMap<>();
    private static final SeedRecord DEFAULT = new SeedRecord(650, 1, 0, false);

    static {
        RECORDS.put("111111111", new SeedRecord(750, 3, 0, false));
        RECORDS.put("222222222", new SeedRecord(650, 2, 0, false));
        RECORDS.put("333333333", new SeedRecord(580, 1, 5, false));
        RECORDS.put("444444444", new SeedRecord(620, 2, 1, false));
        RECORDS.put("555555555", new SeedRecord(700, 4, 0, false));
        RECORDS.put("666666666", new SeedRecord(720, 2, 0, false));
        RECORDS.put("999999999", new SeedRecord(700, 2, 0, true));
    }

    CreditCheckController.CreditCheckResponse score(String ssn, Long customerId) {
        SeedRecord rec = RECORDS.getOrDefault(ssn, DEFAULT);
        return new CreditCheckController.CreditCheckResponse(
                rec.score(),
                new CreditCheckController.BureauReport(rec.openAccounts(), rec.latePayments(), rec.bankruptcy()));
    }

    private record SeedRecord(int score, int openAccounts, int latePayments, boolean bankruptcy) {}
}
