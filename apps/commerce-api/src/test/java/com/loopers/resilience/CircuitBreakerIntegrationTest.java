package com.loopers.resilience;

import com.loopers.domain.payment.PaymentClient;
import com.loopers.domain.payment.PgApproveRequest;
import com.loopers.domain.payment.PgApproveResult;
import com.loopers.domain.payment.PgCancelResult;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgQueryResult;
import com.loopers.domain.payment.PgServerException;
import com.loopers.domain.payment.PgTimeoutException;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentRepository;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 서킷브레이커 동작 검증 테스트
 *
 * application.yml 설정과 동일한 값으로 CircuitBreaker를 프로그래밍 방식으로 생성하여
 * 설정값이 의도대로 동작하는지 검증한다.
 *
 * 3축 기반:
 * - 축 1: CB 있을 때 장애 격리 (PG 호출 차단)
 * - 축 2: 설정값별 동작 시나리오 (상태 전이)
 * - 축 3: 복구 (HALF_OPEN → CLOSED)
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CircuitBreakerIntegrationTest {

    private ControllablePaymentClient paymentClient;
    private PaymentService paymentService;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        paymentClient = new ControllablePaymentClient();

        // application.yml과 동일한 설정
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .maxWaitDurationInHalfOpenState(Duration.ofSeconds(60))
                .automaticTransitionFromOpenToHalfOpenEnabled(false) // 테스트에서는 수동 전이
                .recordExceptions(PgServerException.class, PgTimeoutException.class)
                .ignoreExceptions(PgClientException.class)
                .build();

        circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("pgPayment-test");

        PaymentRepository mockRepo = Mockito.mock(PaymentRepository.class);
        paymentService = new PaymentService(mockRepo, paymentClient);
    }

    // ── 축 2: 서킷 상태 전이 ──────────────────────────────────────────────

    @DisplayName("축 2: 서킷 상태 전이")
    @Nested
    class 서킷_상태_전이 {

        @Test
        @DisplayName("PG 500이 5건 연속 발생하면 서킷이 OPEN된다 (slidingWindow=10, threshold=50%)")
        void PG_500이_5건_연속이면_서킷이_OPEN된다() {
            // arrange
            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);

            // act — 5건 실패 (minimumNumberOfCalls=5, failureRate=100% > 50%)
            for (int i = 0; i < 5; i++) {
                callWithCB_ignoreException();
            }

            // assert
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(100.0f);
        }

        @Test
        @DisplayName("PG 500이 4건만 발생하면 서킷은 CLOSED 유지 (minimumNumberOfCalls=5 미충족)")
        void 최소_호출수_미달이면_서킷은_CLOSED_유지() {
            // arrange
            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);

            // act — 4건만 실패
            for (int i = 0; i < 4; i++) {
                callWithCB_ignoreException();
            }

            // assert — 5건 미만이므로 실패율 평가 안 함
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0f);
        }

        @Test
        @DisplayName("5건 중 2건만 실패하면 서킷은 CLOSED 유지 (40% < threshold 50%)")
        void 실패율_미달이면_서킷은_CLOSED_유지() {
            // arrange — 3건 성공 후 2건 실패
            paymentClient.setMode(ControllablePaymentClient.Mode.SUCCESS);
            for (int i = 0; i < 3; i++) {
                callWithCB_ignoreException();
            }

            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);
            for (int i = 0; i < 2; i++) {
                callWithCB_ignoreException();
            }

            // assert — 2/5 = 40% < 50%
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(40.0f);
        }

        @Test
        @DisplayName("5건 중 3건 실패하면 서킷이 OPEN된다 (60% > threshold 50%)")
        void 실패율_초과면_서킷_OPEN() {
            // arrange — 2건 성공 후 3건 실패
            paymentClient.setMode(ControllablePaymentClient.Mode.SUCCESS);
            for (int i = 0; i < 2; i++) {
                callWithCB_ignoreException();
            }

            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);
            for (int i = 0; i < 3; i++) {
                callWithCB_ignoreException();
            }

            // assert — 3/5 = 60% > 50%
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    // ── 축 1: 장애 격리 ──────────────────────────────────────────────────

    @DisplayName("축 1: 장애 격리")
    @Nested
    class 장애_격리 {

        @Test
        @DisplayName("서킷 OPEN 상태에서 호출하면 CallNotPermittedException이 발생한다")
        void 서킷_OPEN이면_즉시_차단() {
            // arrange — 서킷 강제 OPEN
            circuitBreaker.transitionToOpenState();
            paymentClient.setMode(ControllablePaymentClient.Mode.SUCCESS);
            int callCountBefore = paymentClient.getApproveCallCount();

            // act & assert
            assertThatThrownBy(() -> circuitBreaker.executeRunnable(() -> {
                Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-OPEN");
                paymentService.requestPayment(payment, createRequest());
            })).isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);

            // PG 호출이 나가지 않았다
            assertThat(paymentClient.getApproveCallCount()).isEqualTo(callCountBefore);
        }

        @Test
        @DisplayName("서킷 OPEN 후 연속 호출해도 PG에 요청이 나가지 않는다 (불필요한 호출 제거)")
        void 서킷_OPEN이면_연속_호출도_차단() {
            // arrange
            circuitBreaker.transitionToOpenState();
            int callCountBefore = paymentClient.getApproveCallCount();

            // act — 10건 연속 시도
            int blockedCount = 0;
            for (int i = 0; i < 10; i++) {
                try {
                    circuitBreaker.executeRunnable(() -> {
                        Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-" + UUID.randomUUID());
                        paymentService.requestPayment(payment, createRequest());
                    });
                } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
                    blockedCount++;
                }
            }

            // assert — 10건 전부 차단, PG 호출 0건
            assertThat(blockedCount).isEqualTo(10);
            assertThat(paymentClient.getApproveCallCount()).isEqualTo(callCountBefore);
        }
    }

    // ── 함정3 방지: 비즈니스 예외 ────────────────────────────────────────

    @DisplayName("함정3 방지: 비즈니스 예외")
    @Nested
    class 비즈니스_예외_분리 {

        @Test
        @DisplayName("PG 4xx(비즈니스 거절)는 서킷 실패로 집계되지 않는다")
        void PG_4xx는_서킷에_집계되지_않는다() {
            // arrange
            paymentClient.setMode(ControllablePaymentClient.Mode.CLIENT_ERROR);

            // act — 10건 전부 4xx (PaymentService에서 catch → FAILED 반환, 예외 아님)
            for (int i = 0; i < 10; i++) {
                callWithCB_ignoreException();
            }

            // assert — 4xx는 ignoreExceptions → 서킷 CLOSED 유지
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        }

        @Test
        @DisplayName("PG 4xx는 PaymentService에서 catch → CB에 '성공'으로 집계되어 실패율을 희석한다")
        void PG_4xx는_CB_성공으로_집계되어_실패율_희석() {
            // 4xx는 PaymentService에서 catch → PaymentResult.FAILED 반환 → CB에는 예외 없음 → "성공"
            // 따라서 4xx 3건 = CB 성공 3건, 500 2건 = CB 실패 2건 → 2/5 = 40% < 50% → CLOSED
            paymentClient.setMode(ControllablePaymentClient.Mode.CLIENT_ERROR);
            for (int i = 0; i < 3; i++) {
                callWithCB_ignoreException();
            }

            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);
            for (int i = 0; i < 2; i++) {
                callWithCB_ignoreException();
            }

            // assert — 2/5 = 40% < 50% → CLOSED
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(40.0f);
            assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
            assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(3);
        }
    }

    // ── 축 3: 복구 ──────────────────────────────────────────────────────

    @DisplayName("축 3: 복구")
    @Nested
    class 복구 {

        @Test
        @DisplayName("HALF_OPEN에서 3건 성공하면 CLOSED로 복구된다")
        void HALF_OPEN에서_성공하면_CLOSED로_복구() {
            // arrange
            circuitBreaker.transitionToOpenState();
            circuitBreaker.transitionToHalfOpenState();
            paymentClient.setMode(ControllablePaymentClient.Mode.SUCCESS);

            // act — permitted 3건 성공
            for (int i = 0; i < 3; i++) {
                callWithCB_ignoreException();
            }

            // assert
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("HALF_OPEN에서 2건 실패하면 다시 OPEN으로 돌아간다 (2/3 = 67% > 50%)")
        void HALF_OPEN에서_실패하면_다시_OPEN() {
            // arrange
            circuitBreaker.transitionToOpenState();
            circuitBreaker.transitionToHalfOpenState();

            // act — 1건 성공 + 2건 실패
            paymentClient.setMode(ControllablePaymentClient.Mode.SUCCESS);
            callWithCB_ignoreException();

            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);
            for (int i = 0; i < 2; i++) {
                callWithCB_ignoreException();
            }

            // assert — 2/3 = 67% > 50%
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }

        @Test
        @DisplayName("HALF_OPEN에서 1건만 실패하면 CLOSED로 복구된다 (1/3 = 33% < 50%)")
        void HALF_OPEN에서_1건_실패면_CLOSED() {
            // arrange
            circuitBreaker.transitionToOpenState();
            circuitBreaker.transitionToHalfOpenState();

            // act — 2건 성공 + 1건 실패
            paymentClient.setMode(ControllablePaymentClient.Mode.SUCCESS);
            for (int i = 0; i < 2; i++) {
                callWithCB_ignoreException();
            }

            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);
            callWithCB_ignoreException();

            // assert — 1/3 = 33% < 50%
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    // ── 예외 전파 설계 검증 ──────────────────────────────────────────────

    @DisplayName("예외 전파 설계")
    @Nested
    class 예외_전파 {

        @Test
        @DisplayName("타임아웃 후 query 복구 성공 시 CB는 성공으로 집계한다")
        void 타임아웃_후_query_복구_성공이면_CB_성공() {
            // arrange — approve는 타임아웃, query는 성공 반환
            paymentClient.setMode(ControllablePaymentClient.Mode.TIMEOUT_THEN_QUERY_SUCCESS);

            // act — 5건 모두 타임아웃 → query 복구 → PaymentResult 반환 (예외 아님)
            for (int i = 0; i < 5; i++) {
                callWithCB_ignoreException();
            }

            // assert — query로 복구됐으므로 CB 실패 0건 → CLOSED 유지
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
            assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        }

        @Test
        @DisplayName("PG 500은 PaymentService를 뚫고 전파된다")
        void PG_500은_Service를_뚫고_전파() {
            // arrange
            paymentClient.setMode(ControllablePaymentClient.Mode.SERVER_ERROR);

            // act & assert
            Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-500");
            assertThatThrownBy(() -> paymentService.requestPayment(payment, createRequest()))
                    .isInstanceOf(PgServerException.class);
        }

        @Test
        @DisplayName("PG 4xx는 PaymentService에서 catch되어 FAILED를 반환한다")
        void PG_4xx는_Service에서_catch() {
            // arrange
            paymentClient.setMode(ControllablePaymentClient.Mode.CLIENT_ERROR);

            // act
            Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-4xx");
            PaymentResult result = paymentService.requestPayment(payment, createRequest());

            // assert
            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("타임아웃 후 query도 실패하면 PgTimeoutException이 전파된다")
        void 타임아웃_후_query도_실패하면_전파() {
            // arrange — approve 타임아웃 + query도 NOT_FOUND (null transactionKey)
            paymentClient.setMode(ControllablePaymentClient.Mode.TIMEOUT);

            // act & assert
            Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-TO");
            assertThatThrownBy(() -> paymentService.requestPayment(payment, createRequest()))
                    .isInstanceOf(PgTimeoutException.class);
        }
    }

    // ── Bulkhead 동시성 검증 ──────────────────────────────────────────────

    @DisplayName("Bulkhead 동시성 검증")
    @Nested
    class Bulkhead_동시성 {

        private Bulkhead bulkhead;

        @BeforeEach
        void setUpBulkhead() {
            BulkheadConfig bulkheadConfig = BulkheadConfig.custom()
                    .maxConcurrentCalls(20)
                    .maxWaitDuration(Duration.ofMillis(0)) // fail-fast
                    .build();
            bulkhead = BulkheadRegistry.of(bulkheadConfig).bulkhead("pgPayment-test");
        }

        @Test
        @DisplayName("동시 30건 중 20건만 통과하고 10건은 BulkheadFullException으로 거절된다")
        void 동시_호출이_maxConcurrentCalls를_초과하면_거절된다() throws InterruptedException {
            // arrange — SLOW 모드: approve()에서 2초 지연 → 세마포어 점유 유지
            paymentClient.setMode(ControllablePaymentClient.Mode.SLOW);

            int totalCalls = 30;
            CountDownLatch readyLatch = new CountDownLatch(totalCalls);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalCalls);
            AtomicInteger passedCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(totalCalls);

            // act — 30개 스레드가 동시에 Bulkhead 진입 시도
            for (int i = 0; i < totalCalls; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    try {
                        bulkhead.executeRunnable(() -> {
                            Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-" + UUID.randomUUID());
                            paymentService.requestPayment(payment, createRequest());
                        });
                        passedCount.incrementAndGet();
                    } catch (BulkheadFullException e) {
                        rejectedCount.incrementAndGet();
                    } catch (Exception e) {
                        // PG 예외는 Bulkhead 통과 후 발생 → "통과"로 집계
                        passedCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);  // 30개 스레드 준비 대기
            startLatch.countDown();                   // 동시 시작
            doneLatch.await(10, TimeUnit.SECONDS);    // 전체 완료 대기
            executor.shutdown();

            // assert — maxConcurrentCalls=20 → 20건 통과, 10건 거절
            assertThat(passedCount.get()).isEqualTo(20);
            assertThat(rejectedCount.get()).isEqualTo(10);
        }

        @Test
        @DisplayName("Bulkhead 거절 시 PG 호출이 발생하지 않는다 (스레드 보호)")
        void Bulkhead_거절되면_PG_호출_없음() throws InterruptedException {
            // arrange — SLOW 모드로 20개 슬롯을 점유시킨 뒤 추가 10건 시도
            paymentClient.setMode(ControllablePaymentClient.Mode.SLOW);

            int totalCalls = 30;
            CountDownLatch readyLatch = new CountDownLatch(totalCalls);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(totalCalls);

            ExecutorService executor = Executors.newFixedThreadPool(totalCalls);

            for (int i = 0; i < totalCalls; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    try {
                        bulkhead.executeRunnable(() -> {
                            Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-" + UUID.randomUUID());
                            paymentService.requestPayment(payment, createRequest());
                        });
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // assert — Bulkhead를 통과한 20건만 PG approve()를 호출했다
            assertThat(paymentClient.getApproveCallCount()).isEqualTo(20);
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────

    /**
     * CB를 통해 PaymentService.requestPayment()를 호출한다.
     * 예외가 발생하면 CB에 기록된 후 무시한다 (테스트에서 상태 전이만 검증).
     */
    private void callWithCB_ignoreException() {
        try {
            circuitBreaker.executeRunnable(() -> {
                Payment payment = Payment.request(1L, 10000, "CARD", "IDEM-" + UUID.randomUUID());
                paymentService.requestPayment(payment, createRequest());
            });
        } catch (PgServerException | PgTimeoutException |
                 io.github.resilience4j.circuitbreaker.CallNotPermittedException ignored) {
            // CB에 기록된 후 re-throw된 예외만 무시
        }
    }

    private PgApproveRequest createRequest() {
        return new PgApproveRequest(1L, "ORD-TEST", "CARD", "0000-0000", 10000, null);
    }

    /**
     * 테스트용 PaymentClient — 모드를 전환하여 PG 응답을 제어한다.
     */
    static class ControllablePaymentClient implements PaymentClient {

        enum Mode {
            SUCCESS,
            SERVER_ERROR,
            CLIENT_ERROR,
            TIMEOUT,
            TIMEOUT_THEN_QUERY_SUCCESS,
            SLOW
        }

        private volatile Mode mode = Mode.SUCCESS;
        private final AtomicInteger approveCallCount = new AtomicInteger(0);

        void setMode(Mode mode) {
            this.mode = mode;
        }

        int getApproveCallCount() {
            return approveCallCount.get();
        }

        @Override
        public PgApproveResult approve(PgApproveRequest request) {
            approveCallCount.incrementAndGet();
            return switch (mode) {
                case SUCCESS -> PgApproveResult.pending("TXN-" + UUID.randomUUID().toString().substring(0, 8));
                case SERVER_ERROR -> throw new PgServerException("PG 서버 에러 (테스트)");
                case CLIENT_ERROR -> throw new PgClientException("잔액 부족 (테스트)");
                case TIMEOUT, TIMEOUT_THEN_QUERY_SUCCESS -> throw new PgTimeoutException("PG 타임아웃 (테스트)");
                case SLOW -> {
                    try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    yield PgApproveResult.pending("TXN-SLOW-" + UUID.randomUUID().toString().substring(0, 8));
                }
            };
        }

        @Override
        public PgQueryResult query(String transactionKey, Long userId) {
            if (mode == Mode.TIMEOUT_THEN_QUERY_SUCCESS) {
                return PgQueryResult.success("TXN-RECOVERED", "조회 복구 성공");
            }
            if (transactionKey == null) {
                return PgQueryResult.notFound();
            }
            return PgQueryResult.success(transactionKey, "테스트 승인");
        }

        @Override
        public PgCancelResult cancel(String transactionKey, Long userId) {
            return PgCancelResult.ofSuccess();
        }
    }
}
