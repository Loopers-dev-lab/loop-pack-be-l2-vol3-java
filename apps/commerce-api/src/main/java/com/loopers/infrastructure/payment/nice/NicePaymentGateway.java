package com.loopers.infrastructure.payment.nice;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;

@Component
public class NicePaymentGateway implements PaymentGateway {

    private static final Set<String> RETRYABLE_RESULT_CODES = Set.of(
            "A110",   // 외부 연동결과 실패
            "9002",   // Try-Catch-Exception
            "U508"    // 소켓 연결 오류
    );

    private final RestTemplate niceRestTemplate;
    private final NiceProperties niceProperties;
    private final ObjectMapper objectMapper;

    public NicePaymentGateway(
            @Qualifier("niceRestTemplate") RestTemplate niceRestTemplate,
            NiceProperties niceProperties,
            ObjectMapper objectMapper) {
        this.niceRestTemplate = niceRestTemplate;
        this.niceProperties = niceProperties;
        this.objectMapper = objectMapper;
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
    @Retry(name = "nice-confirm")
    @Override
    public PgResult.Confirm confirm(PgCommand.Confirm command) {
        try {
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
            return PgResult.Confirm.of(success, command.paymentKey(),
                    success ? null : (response != null ? response.resultMsg() : "PG 승인 실패"),
                    response != null ? response.amount() : null);
        } catch (HttpClientErrorException e) {
            throw classifyClientError(e, "나이스 결제 승인", command.paymentKey());
        } catch (HttpServerErrorException e) {
            String resultCode = parseResultCode(e.getResponseBodyAsString());
            throw new PgCommunicationException("나이스 결제 승인 서버 오류: resultCode=" + resultCode
                    + ", paymentKey=" + command.paymentKey(), e);
        } catch (ResourceAccessException e) {
            throw new PgTimeoutException("나이스 결제 승인 타임아웃: paymentKey=" + command.paymentKey(), e);
        } catch (RestClientException e) {
            throw new PgCommunicationException("나이스 결제 승인 통신 실패: paymentKey=" + command.paymentKey(), e);
        }
    }

    @Retry(name = "nice-cancel")
    @Override
    public PgResult.Cancel cancel(String paymentKey, PgCommand.Cancel command) {
        try {
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

            return PgResult.Cancel.of(true, null);
        } catch (HttpClientErrorException e) {
            throw classifyClientError(e, "나이스 결제 취소", paymentKey);
        } catch (HttpServerErrorException e) {
            String resultCode = parseResultCode(e.getResponseBodyAsString());
            throw new PgCommunicationException("나이스 결제 취소 서버 오류: resultCode=" + resultCode
                    + ", paymentKey=" + paymentKey, e);
        } catch (ResourceAccessException e) {
            throw new PgTimeoutException("나이스 결제 취소 타임아웃: paymentKey=" + paymentKey, e);
        } catch (RestClientException e) {
            throw new PgCommunicationException("나이스 결제 취소 통신 실패: paymentKey=" + paymentKey, e);
        }
    }

    // Query

    @CircuitBreaker(name = "nice-query", fallbackMethod = "queryFallback")
    @Retry(name = "nice-query")
    @Override
    public PgResult.Query query(String paymentKey) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(null);

            ResponseEntity<NicePaymentResponse> response = niceRestTemplate.exchange(
                    niceProperties.baseUrl() + "/v1/payments/" + paymentKey,
                    HttpMethod.GET,
                    entity,
                    NicePaymentResponse.class
            );

            NicePaymentResponse body = response.getBody();
            if (body == null || !body.isSuccess()) {
                return PgResult.Query.of(false, false, null, null);
            }
            return PgResult.Query.of(true, body.isPaid(), body.status(), body.amount());
        } catch (HttpClientErrorException.NotFound e) {
            return PgResult.Query.of(false, false, null, null);
        } catch (HttpClientErrorException e) {
            throw classifyClientError(e, "나이스 결제 조회", paymentKey);
        } catch (HttpServerErrorException e) {
            String resultCode = parseResultCode(e.getResponseBodyAsString());
            throw new PgCommunicationException("나이스 결제 조회 서버 오류: resultCode=" + resultCode
                    + ", paymentKey=" + paymentKey, e);
        } catch (ResourceAccessException e) {
            throw new PgTimeoutException("나이스 결제 조회 타임아웃: paymentKey=" + paymentKey, e);
        } catch (RestClientException e) {
            throw new PgCommunicationException("나이스 결제 조회 통신 실패: paymentKey=" + paymentKey, e);
        }
    }

    private RuntimeException classifyClientError(HttpClientErrorException e, String operation, String paymentKey) {
        String body = e.getResponseBodyAsString();
        String resultCode = parseResultCode(body);

        if (RETRYABLE_RESULT_CODES.contains(resultCode)) {
            return new PgCommunicationException(operation + " 일시적 오류: resultCode=" + resultCode
                    + ", paymentKey=" + paymentKey, e);
        }
        return new PgBusinessException(operation + " 거절: resultCode=" + resultCode
                + ", paymentKey=" + paymentKey + ", body=" + body, e);
    }

    private String parseResultCode(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.has("resultCode") ? node.get("resultCode").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private PgResult.Confirm confirmFallback(PgCommand.Confirm command, Throwable t) {
        throw new PgUnavailableException("나이스 서킷 OPEN — 결제 승인 불가: paymentKey=" + command.paymentKey(), t);
    }

    private PgResult.Query queryFallback(String paymentKey, Throwable t) {
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
    }
}
