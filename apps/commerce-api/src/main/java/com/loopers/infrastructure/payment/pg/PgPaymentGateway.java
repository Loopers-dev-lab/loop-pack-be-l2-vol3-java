package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayRetryableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class PgPaymentGateway implements PaymentGateway {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String callbackUrl;

    public PgPaymentGateway(
        RestTemplate pgRestTemplate,
        @Value("${pg.base-url}") String baseUrl,
        @Value("${pg.callback-url}") String callbackUrl
    ) {
        this.restTemplate = pgRestTemplate;
        this.baseUrl = baseUrl;
        this.callbackUrl = callbackUrl;
    }

    /**
     * PG에 결제를 요청한다.
     * @Retry 미적용: PG가 orderId 멱등성을 보장하지 않으므로 재시도하면 이중 결제 위험.
     * @CircuitBreaker만 적용: 반복 실패 시 차단하여 장애 확산 방지.
     */
    @CircuitBreaker(name = "pgCircuit")
    @Override
    public String requestPayment(Long userId, Long orderId, CardType cardType, String cardNo, int amount) {
        String url = baseUrl + "/api/v1/payments";

        PgPaymentRequest request = new PgPaymentRequest(
            formatOrderId(orderId),
            cardType.name(),
            cardNo,
            amount,
            callbackUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-USER-ID", String.valueOf(userId));

        HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(request, headers);

        try {
            long startTime = System.currentTimeMillis();

            ResponseEntity<PgApiResponse<PgTransactionResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<>() {}
            );

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[PG 결제 요청] orderId={}, 응답시간={}ms", orderId, elapsed);

            PgApiResponse<PgTransactionResponse> body = response.getBody();
            if (body == null || !body.isSuccess()) {
                String errorMsg = body != null ? body.meta().message() : "응답 없음";
                throw new PaymentGatewayException("PG 결제 요청 실패: " + errorMsg);
            }

            return body.data().transactionKey();

        } catch (ResourceAccessException e) {
            log.warn("[PG 타임아웃] orderId={}, error={}", orderId, e.getMessage());
            throw new PaymentGatewayException("PG 요청 타임아웃: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.warn("[PG 서버 에러] orderId={}, status={}", orderId, e.getStatusCode());
            throw new PaymentGatewayException("PG 서버 에러: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.warn("[PG 요청 실패] orderId={}, error={}", orderId, e.getMessage());
            throw new PaymentGatewayException("PG 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * PG에서 transactionKey로 결제 상태를 조회한다.
     * GET 조회는 멱등하므로 @Retry 적용.
     * 타임아웃/5xx만 재시도하고 4xx는 재시도하지 않음.
     */
    @Retry(name = "pgRetry")
    @Override
    public TransactionResult getTransactionStatus(Long userId, String transactionKey) {
        String url = baseUrl + "/api/v1/payments/" + transactionKey;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<PgApiResponse<PgTransactionDetailResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
            );

            PgApiResponse<PgTransactionDetailResponse> body = response.getBody();
            if (body == null || !body.isSuccess()) {
                throw new PaymentGatewayException("PG 결제 조회 실패");
            }

            PgTransactionDetailResponse data = body.data();
            return new TransactionResult(data.transactionKey(), data.status(), data.reason());

        } catch (ResourceAccessException e) {
            log.warn("[PG 조회 타임아웃] transactionKey={}, error={}", transactionKey, e.getMessage());
            throw new PaymentGatewayRetryableException("PG 조회 타임아웃: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.warn("[PG 조회 서버 에러] transactionKey={}, status={}", transactionKey, e.getStatusCode());
            throw new PaymentGatewayRetryableException("PG 조회 서버 에러: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.warn("[PG 조회 실패] transactionKey={}, error={}", transactionKey, e.getMessage());
            throw new PaymentGatewayException("PG 조회 실패: " + e.getMessage(), e);
        }
    }

    /**
     * PG에서 orderId로 결제 목록을 조회한다.
     * PENDING Payment(transactionKey 미보유) 복구 시 사용.
     */
    @Retry(name = "pgRetry")
    @Override
    public List<TransactionResult> getTransactionsByOrderId(Long userId, Long orderId) {
        String url = baseUrl + "/api/v1/payments?orderId=" + formatOrderId(orderId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", String.valueOf(userId));

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<PgApiResponse<PgOrderResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<>() {}
            );

            PgApiResponse<PgOrderResponse> body = response.getBody();
            if (body == null || !body.isSuccess()) {
                // 조회 실패 → "판단 불가". emptyList가 아닌 예외를 던져 "진짜 0건"과 구분
                String errorMsg = body != null ? body.meta().message() : "응답 없음";
                throw new PaymentGatewayException("PG orderId 조회 실패: " + errorMsg);
            }

            PgOrderResponse data = body.data();
            if (data == null || data.transactions() == null || data.transactions().isEmpty()) {
                return Collections.emptyList(); // 정상 응답이지만 결제 없음 → 진짜 0건
            }

            return data.transactions().stream()
                .map(t -> new TransactionResult(t.transactionKey(), t.status(), t.reason()))
                .toList();

        } catch (ResourceAccessException e) {
            log.warn("[PG orderId 조회 타임아웃] orderId={}, error={}", orderId, e.getMessage());
            throw new PaymentGatewayRetryableException("PG orderId 조회 타임아웃: " + e.getMessage(), e);
        } catch (HttpServerErrorException e) {
            log.warn("[PG orderId 조회 서버 에러] orderId={}, status={}", orderId, e.getStatusCode());
            throw new PaymentGatewayRetryableException("PG orderId 조회 서버 에러: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.warn("[PG orderId 조회 실패] orderId={}, error={}", orderId, e.getMessage());
            throw new PaymentGatewayException("PG orderId 조회 실패: " + e.getMessage(), e);
        }
    }

    private String formatOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }
}
