package com.mockbank.creditcard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CreditCheckClient {

    private final RestClient client;

    public CreditCheckClient(@Value("${credit.service.base-url}") String baseUrl) {
        this.client = RestClient.create(baseUrl);
    }

    public CreditCheckResponse check(String ssn, Long customerId) {
        return client.get()
                .uri(builder -> builder.path("/credit-check")
                        .queryParam("ssn", ssn)
                        .queryParam("customerId", customerId)
                        .build())
                .retrieve()
                .body(CreditCheckResponse.class);
    }

    public record BureauReport(int openAccounts, int latePayments, boolean bankruptcy) {}

    public record CreditCheckResponse(int score, BureauReport report) {}
}
