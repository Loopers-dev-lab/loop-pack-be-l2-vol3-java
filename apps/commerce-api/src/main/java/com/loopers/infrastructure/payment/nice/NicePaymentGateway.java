package com.loopers.infrastructure.payment.nice;

import com.loopers.domain.payment.gateway.PaymentCancelCommand;
import com.loopers.domain.payment.gateway.PaymentCancelResult;
import com.loopers.domain.payment.gateway.PaymentConfirmCommand;
import com.loopers.domain.payment.gateway.PaymentConfirmResult;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentQueryResult;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.infrastructure.payment.nice.dto.NiceApproveRequest;
import com.loopers.infrastructure.payment.nice.dto.NiceCancelRequest;
import com.loopers.infrastructure.payment.nice.dto.NicePaymentResponse;
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
public class NicePaymentGateway implements PaymentGateway {

    private final RestTemplate niceRestTemplate;
    private final NiceProperties niceProperties;

    public NicePaymentGateway(
            @Qualifier("niceRestTemplate") RestTemplate niceRestTemplate,
            NiceProperties niceProperties) {
        this.niceRestTemplate = niceRestTemplate;
        this.niceProperties = niceProperties;
    }

    @Override
    public PgType getType() {
        return PgType.NICE;
    }

    @Override
    public String getCircuitBreakerName() {
        return "nice-request";
    }

    // Command

    @CircuitBreaker(name = "nice-request", fallbackMethod = "confirmFallback")
    @Override
    public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
        NiceApproveRequest request = new NiceApproveRequest(command.amount());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<NiceApproveRequest> entity = new HttpEntity<>(request, headers);

        NicePaymentResponse response = niceRestTemplate.postForObject(
                niceProperties.baseUrl() + "/v1/payments/" + command.paymentKey(),
                entity,
                NicePaymentResponse.class
        );

        boolean success = response != null && response.isSuccess() && response.isPaid();
        return new PaymentConfirmResult(success, command.paymentKey(),
                success ? null : (response != null ? response.resultMsg() : "PG 승인 실패"));
    }

    @Retry(name = "nice-cancel")
    @Override
    public PaymentCancelResult cancel(String paymentKey, PaymentCancelCommand command) {
        NiceCancelRequest request = new NiceCancelRequest(
                command.cancelReason(), command.orderId(), command.cancelAmount());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<NiceCancelRequest> entity = new HttpEntity<>(request, headers);

        niceRestTemplate.postForObject(
                niceProperties.baseUrl() + "/v1/payments/" + paymentKey + "/cancel",
                entity,
                NicePaymentResponse.class
        );

        return new PaymentCancelResult(true, null);
    }

    // Query

    @CircuitBreaker(name = "nice-query", fallbackMethod = "queryFallback")
    @Retry(name = "nice-query")
    @Override
    public PaymentQueryResult query(String paymentKey) {
        HttpEntity<Void> entity = new HttpEntity<>(null);

        ResponseEntity<NicePaymentResponse> response = niceRestTemplate.exchange(
                niceProperties.baseUrl() + "/v1/payments/" + paymentKey,
                HttpMethod.GET,
                entity,
                NicePaymentResponse.class
        );

        NicePaymentResponse body = response.getBody();
        if (body == null || !body.isSuccess()) {
            return new PaymentQueryResult(false, false, null);
        }
        return new PaymentQueryResult(true, body.isPaid(), body.status());
    }

    private PaymentConfirmResult confirmFallback(PaymentConfirmCommand command, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 결제 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요");
    }

    private PaymentQueryResult queryFallback(String paymentKey, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
    }
}
