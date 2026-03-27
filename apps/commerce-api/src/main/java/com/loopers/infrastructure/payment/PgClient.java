package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@RequiredArgsConstructor
@Component
public class PgClient {

    private final RestTemplate pgRestTemplate;

    public PgPaymentResponse requestPayment(PgPaymentRequest request, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", userId);

        HttpEntity<PgPaymentRequest> httpEntity = new HttpEntity<>(request, headers);
        return pgRestTemplate.postForObject("/api/v1/payments", httpEntity, PgPaymentResponse.class);
    }

    public PgPaymentResponse getPaymentStatus(String transactionKey, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", userId);

        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);
        return pgRestTemplate.exchange(
            "/api/v1/payments/{transactionKey}",
            HttpMethod.GET,
            httpEntity,
            PgPaymentResponse.class,
            transactionKey
        ).getBody();
    }
}
