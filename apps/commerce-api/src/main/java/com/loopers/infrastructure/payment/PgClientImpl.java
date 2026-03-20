package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.loopers.domain.payment.PgClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

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

    @Override
    public Optional<PgPaymentResponse> getPaymentByTransactionKey(String transactionKey) {
        try {
            PgApiResponse response = pgRestClient.get()
                .uri("/api/v1/payments/{key}", transactionKey)
                .header("X-USER-ID", "system")
                .retrieve()
                .body(PgApiResponse.class);
            return toResponse(response);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // PG에 해당 결제 없음
        }
        // 그 외 예외(PG 다운, timeout 등)는 propagate → Recovery에서 catch 후 skip
    }

    @Override
    public Optional<PgPaymentResponse> getPaymentByOrderId(String orderId) {
        try {
            PgApiResponse response = pgRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/payments").queryParam("orderId", orderId).build())
                .header("X-USER-ID", "system")
                .retrieve()
                .body(PgApiResponse.class);
            return toResponse(response);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // PG에 해당 주문의 결제 없음 → timeout 시 PG가 처리 못한 것
        }
    }

    private Optional<PgPaymentResponse> toResponse(PgApiResponse response) {
        if (response == null || response.data() == null) return Optional.empty();
        return Optional.of(new PgPaymentResponse(
            response.data().transactionKey(),
            response.data().status(),
            response.data().reason()
        ));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgApiResponse(PgTransactionData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PgTransactionData(String transactionKey, String status, String reason) {}

    record PgRequestBody(String orderId, String cardType, String cardNo, Long amount, String callbackUrl) {}
}
