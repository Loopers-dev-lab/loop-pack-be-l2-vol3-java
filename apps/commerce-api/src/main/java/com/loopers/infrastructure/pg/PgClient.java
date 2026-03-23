package com.loopers.infrastructure.pg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgClient {

    private final RestTemplate pgRestTemplate;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryTemplate pgQueryRetryTemplate;

    @Value("${pg.base-url}")
    private String pgBaseUrl;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    /**
     * PG에 결제 요청을 보낸다.
     * 서킷 브레이커가 열려 있거나 타임아웃 시 fallback으로 empty를 반환한다.
     * 결제 요청은 멱등하지 않으므로 Retry를 적용하지 않는다.
     *
     * @return PG 트랜잭션 ID (empty이면 요청 실패)
     */
    public Optional<String> requestPayment(Long userId, Long orderId, String cardType, String cardNo, BigDecimal amount) {
        return circuitBreakerFactory.create("pg-payment").run(
            () -> {
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-USER-ID", String.valueOf(userId));
                headers.setContentType(MediaType.APPLICATION_JSON);

                PgPaymentRequest request = PgPaymentRequest.of(orderId, cardType, cardNo, amount, callbackUrl);

                ResponseEntity<PgApiResponse<PgPaymentResponse>> response = pgRestTemplate.exchange(
                    pgBaseUrl + "/api/v1/payments",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
                );

                return Optional.ofNullable(response.getBody())
                    .map(PgApiResponse::data)
                    .map(PgPaymentResponse::transactionKey);
            },
            throwable -> {
                log.warn("[PG] 결제 요청 실패 orderId={}: {}", orderId, throwable.getMessage());
                return Optional.empty();
            }
        );
    }

    /**
     * 주문 ID로 PG 결제 상태를 조회한다.
     * 타임아웃 후 결제건 복구 경로로 활용한다.
     * GET 요청(멱등)이므로 서킷브레이커 내부에서 Retry를 적용한다.
     * 서킷이 OPEN이면 Retry 없이 즉시 fallback.
     */
    public Optional<PgPaymentInfo> getPaymentByOrderId(Long userId, Long orderId) {
        return circuitBreakerFactory.create("pg-query").run(
            () -> pgQueryRetryTemplate.execute(ctx -> {
                if (ctx.getRetryCount() > 0) {
                    log.info("[PG] 결제 상태 조회 재시도 orderId={} ({}회차)", orderId, ctx.getRetryCount() + 1);
                }

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-USER-ID", String.valueOf(userId));

                ResponseEntity<PgApiResponse<PgOrderResponse>> response = pgRestTemplate.exchange(
                    pgBaseUrl + "/api/v1/payments?orderId=" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
                );

                return Optional.ofNullable(response.getBody())
                    .map(PgApiResponse::data)
                    .map(PgOrderResponse::transactions)
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.getLast());
            }),
            throwable -> {
                log.warn("[PG] 결제 상태 조회 실패 orderId={}: {}", orderId, throwable.getMessage());
                return Optional.empty();
            }
        );
    }

    /**
     * PG 트랜잭션 ID로 결제 상태를 조회한다.
     * GET 요청(멱등)이므로 서킷브레이커 내부에서 Retry를 적용한다.
     */
    public Optional<PgPaymentInfo> getPaymentByTransactionId(Long userId, String transactionId) {
        return circuitBreakerFactory.create("pg-query").run(
            () -> pgQueryRetryTemplate.execute(ctx -> {
                if (ctx.getRetryCount() > 0) {
                    log.info("[PG] 결제 상태 조회 재시도 transactionId={} ({}회차)", transactionId, ctx.getRetryCount() + 1);
                }

                HttpHeaders headers = new HttpHeaders();
                headers.set("X-USER-ID", String.valueOf(userId));

                ResponseEntity<PgApiResponse<PgPaymentInfo>> response = pgRestTemplate.exchange(
                    pgBaseUrl + "/api/v1/payments/" + transactionId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
                );

                return Optional.ofNullable(response.getBody())
                    .map(PgApiResponse::data);
            }),
            throwable -> {
                log.warn("[PG] 결제 상태 조회 실패 transactionId={}: {}", transactionId, throwable.getMessage());
                return Optional.empty();
            }
        );
    }
}
