package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.TossCancelRequest;
import com.loopers.infrastructure.payment.dto.TossConfirmRequest;
import com.loopers.infrastructure.payment.dto.TossPaymentResponse;
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
public class TossPaymentClient {

    private final RestTemplate tossRestTemplate;
    private final TossProperties tossProperties;

    public TossPaymentClient(
            @Qualifier("tossRestTemplate") RestTemplate tossRestTemplate,
            TossProperties tossProperties) {
        this.tossRestTemplate = tossRestTemplate;
        this.tossProperties = tossProperties;
    }

    // Command

    @CircuitBreaker(name = "toss-request", fallbackMethod = "confirmFallback")
    public TossPaymentResponse confirmPayment(TossConfirmRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<TossConfirmRequest> entity = new HttpEntity<>(request, headers);

        return tossRestTemplate.postForObject(
                tossProperties.baseUrl() + "/v1/payments/confirm",
                entity,
                TossPaymentResponse.class
        );
    }

    @Retry(name = "toss-cancel")
    public TossPaymentResponse cancelPayment(String paymentKey, TossCancelRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<TossCancelRequest> entity = new HttpEntity<>(request, headers);

        return tossRestTemplate.postForObject(
                tossProperties.baseUrl() + "/v1/payments/" + paymentKey + "/cancel",
                entity,
                TossPaymentResponse.class
        );
    }

    // Query

    @CircuitBreaker(name = "toss-query", fallbackMethod = "getPaymentFallback")
    @Retry(name = "toss-query")
    public TossPaymentResponse getPayment(String paymentKey) {
        HttpEntity<Void> entity = new HttpEntity<>(null);

        ResponseEntity<TossPaymentResponse> response = tossRestTemplate.exchange(
                tossProperties.baseUrl() + "/v1/payments/" + paymentKey,
                HttpMethod.GET,
                entity,
                TossPaymentResponse.class
        );

        return response.getBody();
    }

    private TossPaymentResponse confirmFallback(TossConfirmRequest request, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 결제 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요");
    }

    private TossPaymentResponse getPaymentFallback(String paymentKey, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
    }
}
