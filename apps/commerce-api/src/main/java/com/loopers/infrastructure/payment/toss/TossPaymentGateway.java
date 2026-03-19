package com.loopers.infrastructure.payment.toss;

import com.loopers.domain.payment.gateway.PaymentCancelCommand;
import com.loopers.domain.payment.gateway.PaymentCancelResult;
import com.loopers.domain.payment.gateway.PaymentConfirmCommand;
import com.loopers.domain.payment.gateway.PaymentConfirmResult;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentQueryResult;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.infrastructure.payment.toss.dto.TossCancelRequest;
import com.loopers.infrastructure.payment.toss.dto.TossConfirmRequest;
import com.loopers.infrastructure.payment.toss.dto.TossPaymentResponse;
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
public class TossPaymentGateway implements PaymentGateway {

    private final RestTemplate tossRestTemplate;
    private final TossProperties tossProperties;

    public TossPaymentGateway(
            @Qualifier("tossRestTemplate") RestTemplate tossRestTemplate,
            TossProperties tossProperties) {
        this.tossRestTemplate = tossRestTemplate;
        this.tossProperties = tossProperties;
    }

    @Override
    public PgType getType() {
        return PgType.TOSS;
    }

    @Override
    public String getCircuitBreakerName() {
        return "toss-request";
    }

    // Command

    @CircuitBreaker(name = "toss-request", fallbackMethod = "confirmFallback")
    @Override
    public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
        TossConfirmRequest request = new TossConfirmRequest(
                command.paymentKey(), command.orderId(), command.amount());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TossConfirmRequest> entity = new HttpEntity<>(request, headers);

        TossPaymentResponse response = tossRestTemplate.postForObject(
                tossProperties.baseUrl() + "/v1/payments/confirm",
                entity,
                TossPaymentResponse.class
        );

        boolean success = response != null && response.isDone();
        return new PaymentConfirmResult(success, command.paymentKey(),
                success ? null : "PG 승인 실패");
    }

    @Retry(name = "toss-cancel")
    @Override
    public PaymentCancelResult cancel(String paymentKey, PaymentCancelCommand command) {
        TossCancelRequest request = new TossCancelRequest(
                command.cancelReason(), command.cancelAmount());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TossCancelRequest> entity = new HttpEntity<>(request, headers);

        tossRestTemplate.postForObject(
                tossProperties.baseUrl() + "/v1/payments/" + paymentKey + "/cancel",
                entity,
                TossPaymentResponse.class
        );

        return new PaymentCancelResult(true, null);
    }

    // Query

    @CircuitBreaker(name = "toss-query", fallbackMethod = "queryFallback")
    @Retry(name = "toss-query")
    @Override
    public PaymentQueryResult query(String paymentKey) {
        HttpEntity<Void> entity = new HttpEntity<>(null);

        ResponseEntity<TossPaymentResponse> response = tossRestTemplate.exchange(
                tossProperties.baseUrl() + "/v1/payments/" + paymentKey,
                HttpMethod.GET,
                entity,
                TossPaymentResponse.class
        );

        TossPaymentResponse body = response.getBody();
        if (body == null) {
            return new PaymentQueryResult(false, false, null);
        }
        return new PaymentQueryResult(true, body.isDone(), body.status());
    }

    private PaymentConfirmResult confirmFallback(PaymentConfirmCommand command, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 결제 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요");
    }

    private PaymentQueryResult queryFallback(String paymentKey, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
    }
}
