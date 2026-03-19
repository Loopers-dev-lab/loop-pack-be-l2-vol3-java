package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentClient;
import com.loopers.domain.payment.PgApproveRequest;
import com.loopers.domain.payment.PgApproveResult;
import com.loopers.domain.payment.PgCancelResult;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgQueryResult;
import com.loopers.domain.payment.PgServerException;
import com.loopers.domain.payment.PgTimeoutException;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * PG 시뮬레이터 HTTP 구현체 (Infrastructure Layer — Adapter)
 *
 * 예외 전략:
 * - 4xx → PgClientException (비즈니스 거절, CB ignore)
 * - 500 → PgServerException (인프라 장애, CB record)
 * - 타임아웃/연결 실패 → PgTimeoutException (인프라 장애, CB record)
 * - 성공 → Result 객체 반환
 */
@Component
public class PaymentClientImpl implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClientImpl.class);

    private final RestTemplate restTemplate;
    private final String pgBaseUrl;
    private final String callbackBaseUrl;

    public PaymentClientImpl(RestTemplate pgRestTemplate,
                             @Value("${pg.simulator.base-url}") String pgBaseUrl,
                             @Value("${pg.simulator.callback-base-url}") String callbackBaseUrl) {
        this.restTemplate = pgRestTemplate;
        this.pgBaseUrl = pgBaseUrl;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    @Override
    public PgApproveResult approve(PgApproveRequest request) {
        String url = pgBaseUrl + "/api/v1/payments";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", String.valueOf(request.userId()));

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", request.orderId());
        body.put("cardType", request.cardType());
        body.put("cardNo", request.cardNo());
        body.put("amount", request.amount());
        body.put("callbackUrl", callbackBaseUrl + "/api/v1/payments/callback");

        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, httpEntity, Map.class);

            Map<String, Object> data = extractData(response.getBody());
            if (data == null) {
                throw new PgServerException("PG 응답 데이터 없음");
            }

            String status = (String) data.get("status");
            String transactionKey = (String) data.get("transactionKey");

            if ("PENDING".equals(status)) {
                return PgApproveResult.pending(transactionKey);
            }

            throw new PgServerException("예상하지 못한 PG 응답 상태: " + status);

        } catch (HttpClientErrorException e) {
            log.warn("PG 결제 요청 클라이언트 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgClientException(extractErrorMessage(e.getResponseBodyAsString()), e);
        } catch (HttpServerErrorException e) {
            log.warn("PG 서버 에러: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgServerException(extractErrorMessage(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            log.warn("PG 연결 실패/타임아웃: {}", e.getMessage());
            throw new PgTimeoutException("PG 응답 타임아웃", e);
        }
    }

    @Retry(name = "pgQuery")
    @Override
    public PgQueryResult query(String transactionKey, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));
        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        if (transactionKey == null) {
            return PgQueryResult.notFound();
        }

        String url = pgBaseUrl + "/api/v1/payments/" + transactionKey;

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Map.class);

            Map<String, Object> data = extractData(response.getBody());
            if (data == null) {
                throw new PgServerException("PG 조회 응답 데이터 없음");
            }

            String status = (String) data.get("status");
            String reason = (String) data.get("reason");
            String txnKey = (String) data.get("transactionKey");

            return switch (status) {
                case "SUCCESS" -> PgQueryResult.success(txnKey, reason);
                case "PENDING" -> PgQueryResult.pending(txnKey);
                case "FAILED" -> PgQueryResult.failed(txnKey, reason);
                default -> PgQueryResult.error("알 수 없는 PG 상태: " + status);
            };

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return PgQueryResult.notFound();
            }
            log.warn("PG 조회 클라이언트 에러: {}", e.getMessage());
            throw new PgClientException(e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.warn("PG 조회 서버 에러: {}", e.getMessage());
            throw new PgServerException(e.getMessage(), e);
        } catch (ResourceAccessException e) {
            log.warn("PG 조회 연결 실패: {}", e.getMessage());
            throw new PgTimeoutException("PG 조회 타임아웃", e);
        }
    }

    @Override
    public PgCancelResult cancel(String transactionKey, Long userId) {
        log.warn("PG 취소 API 미구현: transactionKey={}", transactionKey);
        return PgCancelResult.ofFailure("PG 시뮬레이터에 취소 API 미제공");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(Map body) {
        if (body == null) {
            return null;
        }
        return (Map<String, Object>) body.get("data");
    }

    private String extractErrorMessage(String responseBody) {
        try {
            if (responseBody != null && responseBody.contains("\"message\"")) {
                int msgStart = responseBody.indexOf("\"message\"") + 11;
                int msgEnd = responseBody.indexOf("\"", msgStart);
                if (msgEnd > msgStart) {
                    return responseBody.substring(msgStart, msgEnd);
                }
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }
}
