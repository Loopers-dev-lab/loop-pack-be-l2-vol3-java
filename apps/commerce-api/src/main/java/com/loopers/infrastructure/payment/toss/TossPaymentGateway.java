package com.loopers.infrastructure.payment.toss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PgBusinessException;
import com.loopers.domain.payment.gateway.PgCommand;
import com.loopers.domain.payment.gateway.PgCommunicationException;
import com.loopers.domain.payment.gateway.PgResult;
import com.loopers.domain.payment.gateway.PgTimeoutException;
import com.loopers.domain.payment.gateway.PgUnavailableException;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.infrastructure.payment.toss.dto.TossCancelRequest;
import com.loopers.infrastructure.payment.toss.dto.TossConfirmRequest;
import com.loopers.infrastructure.payment.toss.dto.TossPaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

@Component
public class TossPaymentGateway implements PaymentGateway {

    private static final Set<String> RETRYABLE_ERROR_CODES = Set.of(
            "PROVIDER_ERROR",
            "CARD_PROCESSING_ERROR",
            "FAILED_INTERNAL_SYSTEM_PROCESSING"
    );

    private static final Set<String> UNKNOWN_OUTCOME_ERROR_CODES = Set.of(
            "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING",
            "UNKNOWN_PAYMENT_ERROR"
    );

    private final RestTemplate tossRestTemplate;
    private final TossProperties tossProperties;
    private final ObjectMapper objectMapper;

    public TossPaymentGateway(
            @Qualifier("tossRestTemplate") RestTemplate tossRestTemplate,
            TossProperties tossProperties,
            ObjectMapper objectMapper) {
        this.tossRestTemplate = tossRestTemplate;
        this.tossProperties = tossProperties;
        this.objectMapper = objectMapper;
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
    @Retry(name = "toss-confirm")
    @Override
    public PgResult.Confirm confirm(PgCommand.Confirm command) {
        try {
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
            return PgResult.Confirm.of(success, command.paymentKey(),
                    success ? null : (response != null ? "PG 승인 실패: status=" + response.status() : "PG 승인 실패"),
                    response != null ? response.totalAmount() : null);
        } catch (HttpClientErrorException e) {
            throw classifyClientError(e, "토스 결제 승인", command.paymentKey());
        } catch (HttpServerErrorException e) {
            throw classifyServerError(e, "토스 결제 승인", command.paymentKey());
        } catch (ResourceAccessException e) {
            throw new PgTimeoutException("토스 결제 승인 타임아웃: paymentKey=" + command.paymentKey(), e);
        } catch (RestClientException e) {
            throw new PgCommunicationException("토스 결제 승인 통신 실패: paymentKey=" + command.paymentKey(), e);
        }
    }

    @Retry(name = "toss-cancel")
    @Override
    public PgResult.Cancel cancel(String paymentKey, PgCommand.Cancel command) {
        try {
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

            return PgResult.Cancel.of(true, null);
        } catch (HttpClientErrorException e) {
            throw classifyClientError(e, "토스 결제 취소", paymentKey);
        } catch (HttpServerErrorException e) {
            throw classifyServerError(e, "토스 결제 취소", paymentKey);
        } catch (ResourceAccessException e) {
            throw new PgTimeoutException("토스 결제 취소 타임아웃: paymentKey=" + paymentKey, e);
        } catch (RestClientException e) {
            throw new PgCommunicationException("토스 결제 취소 통신 실패: paymentKey=" + paymentKey, e);
        }
    }

    // Query

    @CircuitBreaker(name = "toss-query", fallbackMethod = "queryFallback")
    @Retry(name = "toss-query")
    @Override
    public PgResult.Query query(String paymentKey) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(null);

            ResponseEntity<TossPaymentResponse> response = tossRestTemplate.exchange(
                    tossProperties.baseUrl() + "/v1/payments/" + paymentKey,
                    HttpMethod.GET,
                    entity,
                    TossPaymentResponse.class
            );

            TossPaymentResponse body = response.getBody();
            if (body == null) {
                return PgResult.Query.of(false, false, null, null);
            }
            return PgResult.Query.of(true, body.isDone(), body.status(), body.totalAmount());
        } catch (HttpClientErrorException.NotFound e) {
            return PgResult.Query.of(false, false, null, null);
        } catch (HttpClientErrorException e) {
            throw classifyClientError(e, "토스 결제 조회", paymentKey);
        } catch (HttpServerErrorException e) {
            throw classifyServerError(e, "토스 결제 조회", paymentKey);
        } catch (ResourceAccessException e) {
            throw new PgTimeoutException("토스 결제 조회 타임아웃: paymentKey=" + paymentKey, e);
        } catch (RestClientException e) {
            throw new PgCommunicationException("토스 결제 조회 통신 실패: paymentKey=" + paymentKey, e);
        }
    }

    private RuntimeException classifyServerError(HttpServerErrorException e, String operation, String paymentKey) {
        String errorCode = parseErrorCode(e.getResponseBodyAsString());

        if (UNKNOWN_OUTCOME_ERROR_CODES.contains(errorCode)) {
            return new PgTimeoutException(operation + " 결과 미확정: code=" + errorCode
                    + ", paymentKey=" + paymentKey, e);
        }
        return new PgCommunicationException(operation + " 서버 오류: code=" + errorCode
                + ", paymentKey=" + paymentKey, e);
    }

    private RuntimeException classifyClientError(HttpClientErrorException e, String operation, String paymentKey) {
        String body = e.getResponseBodyAsString();
        String errorCode = parseErrorCode(body);

        if (RETRYABLE_ERROR_CODES.contains(errorCode)) {
            return new PgCommunicationException(operation + " 일시적 오류: code=" + errorCode
                    + ", paymentKey=" + paymentKey, e);
        }
        if (UNKNOWN_OUTCOME_ERROR_CODES.contains(errorCode)) {
            return new PgTimeoutException(operation + " 결과 미확정: code=" + errorCode
                    + ", paymentKey=" + paymentKey, e);
        }
        return new PgBusinessException(operation + " 거절: code=" + errorCode
                + ", paymentKey=" + paymentKey + ", body=" + body, e);
    }

    private String parseErrorCode(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.has("code") ? node.get("code").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private PgResult.Confirm confirmFallback(PgCommand.Confirm command, Throwable t) {
        throw new PgUnavailableException("토스 서킷 OPEN — 결제 승인 불가: paymentKey=" + command.paymentKey(), t);
    }

    private PgResult.Query queryFallback(String paymentKey, Throwable t) {
        throw new PgUnavailableException("토스 서킷 OPEN — 결제 조회 불가: paymentKey=" + paymentKey, t);
    }
}
