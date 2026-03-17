package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class PgClient {

    private final RestTemplate pgRestTemplate;
    private final PgProperties pgProperties;

    public PgClient(
            @Qualifier("pgRestTemplate") RestTemplate pgRestTemplate,
            PgProperties pgProperties) {
        this.pgRestTemplate = pgRestTemplate;
        this.pgProperties = pgProperties;
    }

    public PgPaymentResponse requestPayment(Long userId, PgPaymentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", String.valueOf(userId));

        HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(request, headers);

        return pgRestTemplate.postForObject(
                pgProperties.baseUrl() + "/api/v1/payments",
                entity,
                PgPaymentResponse.class
        );
    }
}
