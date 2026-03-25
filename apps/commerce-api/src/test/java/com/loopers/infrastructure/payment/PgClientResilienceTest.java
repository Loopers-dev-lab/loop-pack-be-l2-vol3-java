package com.loopers.infrastructure.payment;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.http.Fault;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PgClient Resilience 테스트 — WireMock으로 PG 장애 시나리오 재현.
 *
 * 목표:
 * 1. Circuit Breaker가 의도한 조건에서 OPEN되는지 확인
 * 2. Aspect 순서(CB > Retry)가 CB 카운팅에 올바르게 영향을 주는지 확인
 * 3. Retry가 실제로 재시도하는 횟수를 WireMock 요청 수로 검증
 *
 * 설정 근거 확인 대상:
 * - minimum-number-of-calls: 5
 * - failure-rate-threshold: 50
 * - max-attempts: 2 (원본 1 + 재시도 1)
 *
 * [주의] @Nested 미사용: WireMockExtension(@RegisterExtension static)은
 * @Nested 클래스의 afterAll에서 서버가 중지되어 후속 @Nested 테스트에서 Connection refused 발생
 */
@SpringBootTest
public class PgClientResilienceTest {

    // WireMock 서버: 동적 포트로 시작 → pg.base-url에 주입
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void configurePgUrl(DynamicPropertyRegistry registry) {
        // PgClientImpl의 base-url을 WireMock 서버로 교체
        registry.add("pg.base-url", wireMock::baseUrl);
        // 테스트용: HALF_OPEN 전이 관찰을 위해 OPEN 대기 시간을 짧게 설정
        registry.add("resilience4j.circuitbreaker.instances.pgPaymentRequest.wait-duration-in-open-state", () -> "2s");
    }

    @Autowired
    private PgClient pgClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private static final Long USER_ID = 1L;
    private static final PgPaymentRequest DUMMY_REQUEST = new PgPaymentRequest(
            "ORDER-001", "SAMSUNG", "4111-1111-1111-1111", 10000L, "http://localhost/callback");

    @BeforeEach
    void resetCircuitBreaker() {
        // 각 테스트마다 CB 상태를 초기화하여 테스트 간 독립성 보장
        circuitBreakerRegistry.circuitBreaker("pgPaymentRequest").reset();
        wireMock.resetAll();
    }

    // ── CircuitBreaker 시나리오 ────────────────────────────────────────────────

    @Test
    @DisplayName("[CB] 5회 연속 실패 후 CircuitBreaker가 OPEN된다")
    void circuitBreaker_opensAfterMinimumCallsReached() {
        // arrange: 모든 POST /api/v1/payments 요청에 500 응답
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // act: minimum-number-of-calls(5)만큼 호출
        for (int i = 0; i < 5; i++) {
            pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
        }

        // assert 1: CB가 OPEN 상태로 전이되었는지
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // assert 2: [핵심] Aspect 순서 검증
        // CB(Retry(call)) 구조 → 사용자 5회 × Retry 2회 = WireMock 10번 요청
        // 만약 Retry(CB(call)) 구조였다면, CB는 10번 기록 → 3번째 사용자 호출에 이미 OPEN
        // → WireMock 요청 수가 6번(3×2)에서 멈춰야 함 (그 이후엔 CB가 요청 차단)
        wireMock.verify(10, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    @Test
    @DisplayName("[CB] 4회 실패로는 CircuitBreaker가 열리지 않는다 (minimum-number-of-calls 미달)")
    void circuitBreaker_staysClosedUnderMinimumCalls() {
        // arrange
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest");

        // act: minimum-number-of-calls(5)보다 1개 적게 호출
        for (int i = 0; i < 4; i++) {
            pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
        }

        // assert: 아직 CLOSED (최소 호출 수 미달)
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // WireMock 요청 수: 4회 × Retry 2회 = 8번
        wireMock.verify(8, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    @Test
    @DisplayName("[CB] OPEN 상태에서는 WireMock에 요청을 보내지 않고 즉시 Fallback을 반환한다")
    void circuitBreaker_rejectRequestsWhenOpen() {
        // arrange: CB를 강제로 OPEN 상태로 전환
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest");
        cb.transitionToOpenState();

        // act
        PgPaymentResponse response = pgClient.requestPayment(USER_ID, DUMMY_REQUEST);

        // assert 1: Fallback이 실행되어 accepted=false 반환
        assertThat(response.accepted()).isFalse();

        // assert 2: WireMock에 실제 HTTP 요청이 0건 (CB가 차단하여 PG 서버에 요청 안 감)
        wireMock.verify(0, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    // ── Retry 시나리오 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("[Retry] 500 에러 발생 시 max-attempts(2)만큼 재시도하여 WireMock에 2번 요청이 기록된다")
    void retry_attemptsCorrectNumberOfTimes() {
        // arrange: 항상 500
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

        // act: 사용자 1회 호출
        pgClient.requestPayment(USER_ID, DUMMY_REQUEST);

        // assert: WireMock에 2번 요청 (원본 1회 + 재시도 1회 = max-attempts: 2)
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    @Test
    @DisplayName("[Retry] 첫 번째 시도 실패 후 재시도에서 성공하면 성공 응답을 반환한다")
    void retry_succeedsOnSecondAttempt() {
        // arrange: 첫 번째 요청은 500, 두 번째는 성공
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .inScenario("retry-then-success")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("retry"));

        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .inScenario("retry-then-success")
                .whenScenarioStateIs("retry")
                .willReturn(okJson("""
                        {
                          "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
                          "data": { "transactionKey": "TX-001", "status": "PENDING", "reason": null }
                        }
                        """)));

        // act
        PgPaymentResponse response = pgClient.requestPayment(USER_ID, DUMMY_REQUEST);

        // assert: 재시도 후 성공
        assertThat(response.accepted()).isTrue();
        assertThat(response.transactionKey()).isEqualTo("TX-001");

        // WireMock: 2번 요청 (1차 실패 + 2차 성공)
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    // ── HALF_OPEN 복구 시나리오 ────────────────────────────────────────────────

    @Test
    @DisplayName("[CB] OPEN → HALF_OPEN 전이 후 성공 요청으로 CLOSED로 복구된다")
    void circuitBreaker_recoversFromOpenThroughHalfOpen() throws InterruptedException {
        // arrange: HALF_OPEN에서 성공 응답을 받을 수 있도록 설정
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(okJson("""
                        {
                          "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
                          "data": { "transactionKey": "TX-RECOVERY", "status": "PENDING", "reason": null }
                        }
                        """)));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest");
        cb.transitionToOpenState();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // act: wait-duration-in-open-state(테스트: 2s) 경과 후 HALF_OPEN 전이 대기
        Thread.sleep(2100);

        // HALF_OPEN에서 permitted-number-of-calls-in-half-open-state(3)만큼 성공 호출
        for (int i = 0; i < 3; i++) {
            PgPaymentResponse response = pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
            assertThat(response.accepted()).isTrue();
        }

        // assert: 3회 성공 → CB가 CLOSED로 복구
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // WireMock: HALF_OPEN에서 3번만 요청 (성공이므로 Retry 없이 각 1번씩)
        wireMock.verify(3, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    // ── Slow Call 시나리오 ────────────────────────────────────────────────────

    @Test
    @DisplayName("[CB] 느린 응답이 slow-call-rate-threshold(80%)를 초과하면 CircuitBreaker가 OPEN된다")
    void circuitBreaker_opensOnSlowCalls() {
        // arrange: slow-call-duration-threshold(2s)를 초과하는 2.1초 지연 응답
        // HTTP 200 OK지만 느린 응답 → CB가 slow call로 카운트
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(okJson("""
                        {
                          "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
                          "data": { "transactionKey": "TX-SLOW", "status": "PENDING", "reason": null }
                        }
                        """).withFixedDelay(2100)));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest");

        // act: minimum-number-of-calls(5)만큼 호출 → 모두 2.1s 소요 (> threshold 2s)
        for (int i = 0; i < 5; i++) {
            pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
        }

        // assert: slow-call-rate 100% > threshold 80% → OPEN
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ── 혼합 시나리오 (실패율 임계치 미달) ──────────────────────────────────────

    @Test
    @DisplayName("[CB] 실패율이 threshold(50%) 미만이면 CircuitBreaker가 CLOSED를 유지한다")
    void circuitBreaker_staysClosedWhenFailureRateBelowThreshold() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest");

        // arrange + act 1: 2회 실패 (500 응답)
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 2; i++) {
            pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
        }

        // arrange + act 2: 3회 성공 (총 5회 = minimum-number-of-calls)
        wireMock.resetAll();
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(okJson("""
                        {
                          "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
                          "data": { "transactionKey": "TX-MIX", "status": "PENDING", "reason": null }
                        }
                        """)));
        for (int i = 0; i < 3; i++) {
            pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
        }

        // assert: 실패율 2/5 = 40% < threshold 50% → CLOSED 유지
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ── 읽기 타임아웃 시나리오 ────────────────────────────────────────────────

    @Test
    @DisplayName("[Timeout] 읽기 타임아웃 발생 시 Retry 없이 즉시 fallback에서 timeout=true를 반환한다")
    void timeout_doesNotRetryAndReturnsFallbackWithTimeoutFlag() {
        // arrange: socketTimeout(3s)을 초과하는 5초 지연 → SocketTimeoutException 발생
        // SocketTimeoutException은 ResourceAccessException으로 감싸져 CB에 전달
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000)));

        // act
        PgPaymentResponse response = pgClient.requestPayment(USER_ID, DUMMY_REQUEST);

        // assert 1: fallback이 실행되어 accepted=false
        assertThat(response.accepted()).isFalse();

        // assert 2: [핵심] timeout=true — PG에 요청이 도달했을 수 있어 결과 불확실 (폴링 대상)
        assertThat(response.timeout()).isTrue();

        // assert 3: [핵심] 타임아웃은 Retry하지 않음 — PG에 요청이 도달했을 수 있어 재시도 시 중복 결제 위험
        // ResilienceRetryConfig의 커스텀 Predicate가 SocketTimeoutException을 재시도 대상에서 제외
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    // ── 연결 끊김 시나리오 ────────────────────────────────────────────────────

    @Test
    @DisplayName("[Connection] 연결 끊김 발생 시 Retry 후 fallback에서 timeout=false를 반환한다")
    void connectionReset_retriesAndReturnsFallbackWithoutTimeoutFlag() {
        // arrange: PG 서버가 연결을 즉시 끊는 상황 (서버 다운, 네트워크 장애 등)
        // CONNECTION_RESET_BY_PEER → ResourceAccessException(IOException) 발생
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        // act
        PgPaymentResponse response = pgClient.requestPayment(USER_ID, DUMMY_REQUEST);

        // assert 1: fallback이 실행되어 accepted=false
        assertThat(response.accepted()).isFalse();

        // assert 2: [핵심] timeout=false — 연결 끊김은 PG가 요청을 처리하지 못했음이 확실
        // fallback에서 t.getCause() instanceof SocketTimeoutException → false
        assertThat(response.timeout()).isFalse();

        // assert 3: Retry가 재시도하여 WireMock에 2번 요청
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/api/v1/payments")));
    }

    // ── HALF_OPEN 복구 실패 시나리오 ──────────────────────────────────────────

    @Test
    @DisplayName("[CB] HALF_OPEN에서 실패하면 다시 OPEN으로 전이된다")
    void circuitBreaker_returnsToOpenWhenHalfOpenFails() throws InterruptedException {
        // arrange: 모든 요청에 500 응답 (HALF_OPEN에서도 실패하도록)
        wireMock.stubFor(post(urlPathEqualTo("/api/v1/payments"))
                .willReturn(aResponse().withStatus(500)));

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest");
        cb.transitionToOpenState();

        // act 1: wait-duration-in-open-state(테스트: 2s) 경과 후 HALF_OPEN 전이 대기
        Thread.sleep(2100);

        // act 2: HALF_OPEN에서 permitted-number-of-calls-in-half-open-state(3)만큼 실패 호출
        for (int i = 0; i < 3; i++) {
            pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
        }

        // assert 1: 3회 모두 실패 → CB가 다시 OPEN으로 전이
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // assert 2: HALF_OPEN에서 3회 × Retry 2회 = WireMock 6번 요청
        wireMock.verify(6, postRequestedFor(urlPathEqualTo("/api/v1/payments")));

        // assert 3: OPEN 전이 후 추가 요청은 차단 (WireMock에 요청 안 감)
        PgPaymentResponse blocked = pgClient.requestPayment(USER_ID, DUMMY_REQUEST);
        assertThat(blocked.accepted()).isFalse();
        wireMock.verify(6, postRequestedFor(urlPathEqualTo("/api/v1/payments"))); // 여전히 6번
    }
}
