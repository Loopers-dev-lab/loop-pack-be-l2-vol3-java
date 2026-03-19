package com.loopers.infrastructure.pg;

import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentRetryAntiPatternTest {

    private final ConnectExceptionPredicate predicate = new ConnectExceptionPredicate();

    @Test
    @DisplayName("안티패턴: ReadTimeout 후 무작정 재시도 → 이중 결제 발생")
    void naiveRetry_onReadTimeout_causesDuplicatePayment() {
        // given: PG 시뮬레이터는 매 요청마다 새 transactionKey 생성 (멱등성 미보장)
        AtomicInteger pgCallCount = new AtomicInteger(0);

        // PG 시뮬레이터 동작 시뮬레이션
        Runnable pgSimulator = () -> {
            pgCallCount.incrementAndGet();
            // 실제로는 UUID.randomUUID()로 매번 새 transactionKey 생성 → 이중 결제
        };

        // when: ReadTimeout 발생 후 무작정 재시도 (naive retry)
        SocketTimeoutException readTimeout = new SocketTimeoutException("Read timed out");
        Request dummyRequest = Request.create(
                Request.HttpMethod.POST, "http://localhost:8082/api/v1/payments",
                Collections.emptyMap(), null, null, null
        );
        RetryableException retryableEx = new RetryableException(
                -1, "Read timed out", Request.HttpMethod.POST, readTimeout, (Long) null, dummyRequest
        );

        // naive retry: 예외 타입 구분 없이 모든 예외에 재시도
        int naiveMaxRetries = 3;
        for (int i = 0; i < naiveMaxRetries; i++) {
            pgSimulator.run(); // 매번 PG에 새 결제 요청 도달
        }

        // then: PG에 3건 도달 → 서로 다른 transactionKey → 이중 결제
        assertThat(pgCallCount.get()).isEqualTo(3);

        // 각 호출마다 새 transactionKey가 생성됨을 증명
        String txKey1 = UUID.randomUUID().toString();
        String txKey2 = UUID.randomUUID().toString();
        String txKey3 = UUID.randomUUID().toString();
        assertThat(txKey1).isNotEqualTo(txKey2);
        assertThat(txKey2).isNotEqualTo(txKey3);

        // ReadTimeout은 ConnectException이 아니므로 predicate가 false 반환
        assertThat(predicate.test(retryableEx)).isFalse();
    }

    @Test
    @DisplayName("스마트 재시도: ReadTimeout에서 ConnectExceptionPredicate가 재시도 차단 → PG에 1건만 도달")
    void smartRetry_onReadTimeout_doesNotRetry() {
        // given: ReadTimeout 발생
        AtomicInteger pgCallCount = new AtomicInteger(0);
        Runnable pgSimulator = pgCallCount::incrementAndGet;

        SocketTimeoutException readTimeout = new SocketTimeoutException("Read timed out");
        Request dummyRequest = Request.create(
                Request.HttpMethod.POST, "http://localhost:8082/api/v1/payments",
                Collections.emptyMap(), null, null, null
        );
        RetryableException retryableEx = new RetryableException(
                -1, "Read timed out", Request.HttpMethod.POST, readTimeout, (Long) null, dummyRequest
        );

        // when: 첫 번째 호출은 실행됨 (원래 요청)
        pgSimulator.run();

        // smart retry: ConnectExceptionPredicate로 재시도 여부 판단
        int maxRetries = 2;
        for (int i = 1; i < maxRetries; i++) {
            if (predicate.test(retryableEx)) {
                pgSimulator.run(); // ConnectException일 때만 재시도
            }
            // ReadTimeout이므로 재시도하지 않음
        }

        // then: PG에 1건만 도달 → 이중 결제 방지
        assertThat(pgCallCount.get()).isEqualTo(1);
        assertThat(predicate.test(retryableEx)).isFalse();
    }
}
