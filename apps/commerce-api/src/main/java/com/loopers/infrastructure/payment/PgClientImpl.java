package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.domain.payment.PgClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Component
public class PgClientImpl implements PgClient {

    private final RestClient pgRestClient;

    @Override
    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        PgApiResponse response = pgRestClient.post()
            .uri("/api/v1/payments")
            .header("X-USER-ID", "system")
            .body(new PgRequestBody(
                request.orderId(),
                request.cardType(),
                request.cardNo(),
                request.amount(),
                request.callbackUrl()
            ))
            .retrieve()
            .body(PgApiResponse.class);

        return new PgPaymentResponse(
            response.data().transactionKey(),
            response.data().status(),
            response.data().reason()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgApiResponse(PgTransactionData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgTransactionData(String transactionKey, String status, String reason) {}

    record PgRequestBody(String orderId, String cardType, String cardNo, Long amount, String callbackUrl) {}
}
