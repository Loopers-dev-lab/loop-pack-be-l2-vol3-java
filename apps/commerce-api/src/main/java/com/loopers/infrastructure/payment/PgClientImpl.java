package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResponse;
import com.loopers.domain.payment.PgOrderStatusResponse;
import com.loopers.domain.payment.PgPaymentStatusResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.SocketTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class PgClientImpl implements PgClient {

    private final RestClient restClient;

    public PgClientImpl(@Value("${pg.base-url}") String pgBaseUrl) {
        // 커넥션 풀 설정 — PG 서버와의 TCP 연결을 재사용
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(20);           // 전체 최대 커넥션 수
        connectionManager.setDefaultMaxPerRoute(20);  // PG 서버 1곳이므로 route당 = 전체
        connectionManager.setDefaultConnectionConfig(
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))    // TCP 연결 타임아웃 3초
                        .setSocketTimeout(Timeout.ofSeconds(3))     // 응답 대기 타임아웃 3초
                        .build()
        );

        // 요청 레벨 설정
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(3))  // 풀에서 커넥션 대기 타임아웃
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        this.restClient = RestClient.builder()
                .baseUrl(pgBaseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    @Retry(name = "pgPaymentRequest")
    @CircuitBreaker(name = "pgPaymentRequest", fallbackMethod = "requestPaymentFallback")
    public PgPaymentResponse requestPayment(Long userId, PgPaymentRequest request) {
        log.info("PG 결제 요청: userId={}, orderId={}, amount={}", userId, request.orderId(), request.amount());

        // PG 시뮬레이터 응답: { meta: { result, errorCode, message }, data: { transactionKey, status, reason } }
        PgApiResponse<PgTransactionResponse> response = restClient.post()
                .uri("/api/v1/payments")
                .header("X-USER-ID", String.valueOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.data() == null) {
            return new PgPaymentResponse(null, false, "PG 응답이 비어있습니다.", false);
        }

        // meta.result == "SUCCESS"이고 data.status == "PENDING"이면 접수 성공
        boolean accepted = "SUCCESS".equals(response.meta().result())
                && "PENDING".equals(response.data().status());

        return new PgPaymentResponse(
                response.data().transactionKey(),
                accepted,
                accepted ? null : response.data().reason(),
                false
        );
    }

    @Override
    @Retry(name = "pgPaymentStatus")
    @CircuitBreaker(name = "pgPaymentStatus", fallbackMethod = "getPaymentStatusFallback")
    public PgPaymentStatusResponse getPaymentStatus(Long userId, String transactionKey) {
        log.info("PG 결제 상태 확인: userId={}, transactionKey={}", userId, transactionKey);

        // PG 시뮬레이터 응답: { meta: {...}, data: { transactionKey, orderId, ..., status, reason } }
        PgApiResponse<PgTransactionDetailResponse> response = restClient.get()
                .uri("/api/v1/payments/{transactionKey}", transactionKey)
                .header("X-USER-ID", String.valueOf(userId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.data() == null) {
            return new PgPaymentStatusResponse(transactionKey, "PENDING", null);
        }

        return new PgPaymentStatusResponse(
                response.data().transactionKey(),
                response.data().status(),
                response.data().reason()
        );
    }

    @Override
    @Retry(name = "pgPaymentStatus")
    @CircuitBreaker(name = "pgPaymentStatus", fallbackMethod = "getPaymentStatusByOrderIdFallback")
    public PgOrderStatusResponse getPaymentStatusByOrderId(Long userId, String orderId) {
        log.info("PG 결제 상태 확인 (orderId 기준): userId={}, orderId={}", userId, orderId);

        // PG 시뮬레이터 응답: { meta: {...}, data: { orderId, transactions: [{ transactionKey, status, reason }] } }
        PgApiResponse<PgOrderResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/payments")
                        .queryParam("orderId", orderId)
                        .build())
                .header("X-USER-ID", String.valueOf(userId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (response == null || response.data() == null) {
            return new PgOrderStatusResponse(orderId, Collections.emptyList());
        }

        List<PgPaymentStatusResponse> transactions = response.data().transactions().stream()
                .map(tx -> new PgPaymentStatusResponse(tx.transactionKey(), tx.status(), tx.reason()))
                .toList();

        return new PgOrderStatusResponse(response.data().orderId(), transactions);
    }

    // 결제 요청 fallback: PG 호출 자체가 실패한 경우 (서킷 OPEN 포함)
    // SocketTimeoutException → timeout=true (PG에 요청이 도달했을 수 있어 결과 불확실, 폴링 대상)
    // 그 외 (HttpServerErrorException 등) → timeout=false (PG가 접수 거부 확정, 즉시 실패 처리)
    private PgPaymentResponse requestPaymentFallback(Long userId, PgPaymentRequest request, Throwable t) {
        boolean isTimeout = t instanceof SocketTimeoutException
                || t.getCause() instanceof SocketTimeoutException;
        log.warn("PG 결제 요청 실패 (fallback): userId={}, orderId={}, timeout={}, error={}",
                userId, request.orderId(), isTimeout, t.getMessage());
        return new PgPaymentResponse(null, false, "PG 연동 실패: " + t.getMessage(), isTimeout);
    }

    // 상태 확인 fallback: PG 상태 확인 API 호출이 실패한 경우
    private PgPaymentStatusResponse getPaymentStatusFallback(Long userId, String transactionKey, Throwable t) {
        log.warn("PG 상태 확인 실패 (fallback): transactionKey={}, error={}", transactionKey, t.getMessage());
        return new PgPaymentStatusResponse(transactionKey, "PENDING", null);
    }

    // orderId 기준 상태 확인 fallback
    private PgOrderStatusResponse getPaymentStatusByOrderIdFallback(Long userId, String orderId, Throwable t) {
        log.warn("PG 상태 확인 실패 (orderId 기준, fallback): orderId={}, error={}", orderId, t.getMessage());
        return new PgOrderStatusResponse(orderId, Collections.emptyList());
    }

    // PG 시뮬레이터 응답 래핑 구조 (인프라 내부용)
    record PgApiResponse<T>(PgMeta meta, T data) {}
    record PgMeta(String result, String errorCode, String message) {}
    record PgTransactionResponse(String transactionKey, String status, String reason) {}
    record PgTransactionDetailResponse(
            String transactionKey, String orderId, String cardType,
            String cardNo, Long amount, String status, String reason) {}
    record PgOrderResponse(String orderId, List<PgOrderTransactionResponse> transactions) {}
    record PgOrderTransactionResponse(String transactionKey, String status, String reason) {}
}
