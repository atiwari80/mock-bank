package com.mockbank.transfer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Calls the fraud-check service. What it does with these inputs is none of our
 * business — we send the signals, it sends back a score and a decision.
 * <p>
 * Velocity is computed here and passed in, which is what keeps that service
 * stateless.
 */
@Component
public class FraudClient {

    private final RestClient client;

    public FraudClient(@Value("${fraud.service.base-url}") String baseUrl) {
        this.client = RestClient.create(baseUrl);
    }

    public FraudCheckResponse check(FraudCheckRequest request) {
        return client.get()
                .uri(builder -> builder.path("/fraud-check")
                        .queryParam("account", request.accountId())
                        .queryParam("amount", request.amount())
                        .queryParam("recipient", request.recipientId())
                        .queryParam("ip", request.ipRisk())
                        .queryParam("recipientIsNew", request.recipientIsNew())
                        .queryParam("recentTransferCount", request.recentTransferCount())
                        .build())
                .retrieve()
                .body(FraudCheckResponse.class);
    }

    public record FraudCheckRequest(
            Long accountId,
            BigDecimal amount,
            Long recipientId,
            boolean recipientIsNew,
            int ipRisk,
            long recentTransferCount) {
    }

    public record FraudCheckResponse(int score, String decision) {

        public boolean isDecline() {
            return "decline".equalsIgnoreCase(decision);
        }

        public boolean isReview() {
            return "review".equalsIgnoreCase(decision);
        }
    }
}
