package com.loopers.infrastructure.payment;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Resilience4j Aspect 순서에 따른 동작 차이를 비교하는 실험 테스트.
 *
 * 구성 A: Retry(바깥) → CB+fallback(안쪽) → PG 호출
 * 구성 B: CB+fallback(바깥) → Retry(안쪽) → PG 호출  (현재 프로젝트 설정)
 *
 * Spring 컨텍스트 없이 Resilience4j 함수형 API로 시뮬레이션한다.
 */
class AspectOrderComparisonTest {

    private static final int TOTAL_REQUESTS = 30;
    private static final double PG_FAILURE_RATE = 0.4; // 40% 실패 = 60% 성공
    private static final long RANDOM_SEED = 42;

    @DisplayName("구성 A vs 구성 B: fallback이 있을 때 Aspect 순서에 따른 동작 차이")
    @Test
    void compareAspectOrders() {
        boolean[] pgOutcomes = generatePgOutcomes(300);

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  Aspect 순서 비교 실험");
        System.out.println("=".repeat(60));
        System.out.printf("PG 실패율      : %.0f%%%n", PG_FAILURE_RATE * 100);
        System.out.printf("요청 수        : %d건%n", TOTAL_REQUESTS);
        System.out.println("CB             : window=10, threshold=50%, min-calls=5");
        System.out.println("Retry          : max-attempts=3");
        System.out.println("Fallback       : @CircuitBreaker에 정의 (응답 객체 반환)");
        System.out.println();

        System.out.println("-".repeat(60));
        System.out.println("  구성 A: Retry(바깥) -> CB+fallback(안쪽) -> PG");
        System.out.println("-".repeat(60));
        SimResult resultA = runSimulation(pgOutcomes.clone(), true);
        System.out.println();

        System.out.println("-".repeat(60));
        System.out.println("  구성 B: CB+fallback(바깥) -> Retry(안쪽) -> PG");
        System.out.println("-".repeat(60));
        SimResult resultB = runSimulation(pgOutcomes.clone(), false);
        System.out.println();

        System.out.println("=".repeat(60));
        System.out.println("  비교 결과");
        System.out.println("=".repeat(60));
        System.out.println();
        printComparison(resultA, resultB);
    }

    private boolean[] generatePgOutcomes(int count) {
        Random rng = new Random(RANDOM_SEED);
        boolean[] outcomes = new boolean[count];
        for (int i = 0; i < count; i++) {
            outcomes[i] = rng.nextDouble() >= PG_FAILURE_RATE;
        }
        return outcomes;
    }

    private SimResult runSimulation(boolean[] pgOutcomes, boolean retryOutside) {
        CircuitBreaker cb = CircuitBreaker.of("test-cb", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .permittedNumberOfCallsInHalfOpenState(2)
                .minimumNumberOfCalls(5)
                .build());

        Retry retry = Retry.of("test-retry", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .build());

        AtomicInteger pgCallIdx = new AtomicInteger(0);
        AtomicInteger totalPgCalls = new AtomicInteger(0);
        AtomicInteger retryCounter = new AtomicInteger(0);
        retry.getEventPublisher().onRetry(event -> retryCounter.incrementAndGet());

        int fallbackCount = 0;
        int successCount = 0;
        int cbOpenAt = -1;

        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            int reqNum = i + 1;
            int pgBefore = totalPgCalls.get();
            CircuitBreaker.State stateBefore = cb.getState();

            Supplier<String> pgCall = () -> {
                int idx = pgCallIdx.getAndIncrement();
                totalPgCalls.incrementAndGet();
                if (idx < pgOutcomes.length && pgOutcomes[idx]) {
                    return "SUCCESS";
                }
                throw new RuntimeException("PG 장애");
            };

            String result;
            if (retryOutside) {
                // 구성 A: Retry(바깥) -> CB+fallback(안쪽) -> PG
                // CB의 fallback이 예외를 잡아서 응답을 반환하므로,
                // Retry는 예외를 볼 수 없어 재시도하지 않는다.
                Supplier<String> cbWithFallback = () -> {
                    try {
                        return cb.executeSupplier(pgCall);
                    } catch (Exception e) {
                        return "FALLBACK";
                    }
                };
                try {
                    result = retry.executeSupplier(cbWithFallback);
                } catch (Exception e) {
                    result = "FALLBACK";
                }
            } else {
                // 구성 B: CB+fallback(바깥) -> Retry(안쪽) -> PG
                // Retry가 안쪽에서 재시도하고, 최종 결과만 CB에 기록된다.
                Supplier<String> retryWrapped = () -> retry.executeSupplier(pgCall);
                try {
                    result = cb.executeSupplier(retryWrapped);
                } catch (Exception e) {
                    result = "FALLBACK";
                }
            }

            boolean isFallback = "FALLBACK".equals(result);
            if (isFallback) fallbackCount++;
            else successCount++;

            int pgThisReq = totalPgCalls.get() - pgBefore;
            CircuitBreaker.State stateAfter = cb.getState();

            String stateChange = "";
            if (stateBefore != CircuitBreaker.State.OPEN && stateAfter == CircuitBreaker.State.OPEN) {
                cbOpenAt = reqNum;
                stateChange = " << OPEN!";
            }

            System.out.printf("  [%2d] PG:%d  %s  CB:%s%s%n",
                    reqNum, pgThisReq,
                    isFallback ? "fallback" : "OK      ",
                    stateAfter, stateChange);
        }

        return new SimResult(totalPgCalls.get(), retryCounter.get(),
                fallbackCount, successCount, cbOpenAt, cb.getState().name());
    }

    private void printComparison(SimResult a, SimResult b) {
        System.out.println("| 항목                | 구성 A (Retry 바깥) | 구성 B (CB 바깥) |");
        System.out.println("|---------------------|---------------------|------------------|");
        System.out.printf("| 총 PG 호출 횟수     | %19d | %16d |%n", a.totalPgCalls, b.totalPgCalls);
        System.out.printf("| 성공 응답            | %19d | %16d |%n", a.successCount, b.successCount);
        System.out.printf("| fallback 응답        | %19d | %16d |%n", a.fallbackCount, b.fallbackCount);
        System.out.printf("| Retry 재시도 횟수    | %19d | %16d |%n", a.retryCount, b.retryCount);
        System.out.printf("| CB OPEN 전이         | %19s | %16s |%n",
                a.cbOpenAt == -1 ? "미전이" : a.cbOpenAt + "번째 요청",
                b.cbOpenAt == -1 ? "미전이" : b.cbOpenAt + "번째 요청");
        System.out.printf("| 최종 CB 상태         | %19s | %16s |%n", a.finalCbState, b.finalCbState);
        System.out.println();
    }

    record SimResult(int totalPgCalls, int retryCount, int fallbackCount,
                     int successCount, int cbOpenAt, String finalCbState) {}
}
