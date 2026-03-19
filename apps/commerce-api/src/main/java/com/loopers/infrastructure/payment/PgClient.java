package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.infrastructure.payment.dto.PgTransactionResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    // Command

    @CircuitBreaker(name = "pg-request", fallbackMethod = "requestPaymentFallback")
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

    // Query

    @CircuitBreaker(name = "pg-query", fallbackMethod = "getTransactionFallback")
    @Retry(name = "pg-query")
    public PgTransactionResponse getTransaction(Long userId, String transactionKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<PgTransactionResponse> response = pgRestTemplate.exchange(
                pgProperties.baseUrl() + "/api/v1/payments/" + transactionKey,
                HttpMethod.GET,
                entity,
                PgTransactionResponse.class
        );

        return response.getBody();
    }

    private PgPaymentResponse requestPaymentFallback(Long userId, PgPaymentRequest request, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 결제 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요");
    }

    private PgTransactionResponse getTransactionFallback(Long userId, String transactionKey, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
    }
}
