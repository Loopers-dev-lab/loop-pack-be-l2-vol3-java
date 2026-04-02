package com.loopers.simulation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("트래픽 전략 비교 시뮬레이션: Rate Limiting vs Queue")
class RateLimitingVsQueueTest {

    private static SimulationResult rl;
    private static SimulationResult q;

    @BeforeAll
    static void runSimulations() {
        var sim = new TrafficSimulator(TrafficSimulator.Config.defaults());
        rl = sim.runRateLimiting();
        q = sim.runQueue();
        printComparisonTable(rl, q);
    }

    @Nested
    @DisplayName("Rate Limiting 전략 검증")
    class RateLimitingAssertions {

        @Test
        @DisplayName("200명이 모두 성공 처리된다.")
        void allUsersSucceed() {
            assertThat(rl.successCount()).isEqualTo(200);
        }

        @Test
        @DisplayName("최대 동시 요청 수는 서버 처리 용량을 초과하지 않는다.")
        void concurrentRequestsDoNotExceedCapacity() {
            assertThat(rl.maxConcurrentRequests()).isLessThanOrEqualTo(80);
        }

        @Test
        @DisplayName("재시도(Retry)가 발생한다 — Thundering Herd 징후.")
        void retriesOccur_thunderingHerdEvidence() {
            assertThat(rl.retryCount()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Queue 전략 검증")
    class QueueAssertions {

        @Test
        @DisplayName("200명이 모두 성공 처리된다.")
        void allUsersSucceed() {
            assertThat(q.successCount()).isEqualTo(200);
        }

        @Test
        @DisplayName("최대 동시 요청 수는 서버 처리 용량 이하다.")
        void concurrentRequestsStayWithinCapacity() {
            assertThat(q.maxConcurrentRequests()).isLessThanOrEqualTo(80);
        }
    }

    @Nested
    @DisplayName("전략 비교 검증")
    class ComparativeAssertions {

        @Test
        @DisplayName("Rate Limiting은 Queue보다 총 요청 수(재시도 포함)가 많다.")
        void rateLimiting_generatesMoreTotalRequests_thanQueue() {
            assertThat(rl.totalRequestsGenerated())
                    .isGreaterThan(q.totalRequestsGenerated());
        }

        @Test
        @DisplayName("Rate Limiting은 Queue보다 재시도 횟수가 많다.")
        void rateLimiting_hasMoreRetries_thanQueue() {
            assertThat(rl.retryCount()).isGreaterThan(q.retryCount());
        }
    }

    private static void printComparisonTable(SimulationResult rl, SimulationResult q) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║            트래픽 전략 비교 시뮬레이션 결과                          ║");
        System.out.println("╠══════════════════════════════════╦═══════════════╦═══════════════╣");
        System.out.printf("║ %-32s ║ %-13s ║ %-13s ║%n", "지표", "Rate Limiting", "Queue");
        System.out.println("╠══════════════════════════════════╬═══════════════╬═══════════════╣");
        System.out.printf("║ %-32s ║ %13d ║ %13d ║%n", "총 발생 요청 수 (재시도 포함)",
                rl.totalRequestsGenerated(), q.totalRequestsGenerated());
        System.out.printf("║ %-32s ║ %13d ║ %13d ║%n", "성공한 요청 수",
                rl.successCount(), q.successCount());
        System.out.printf("║ %-32s ║ %13d ║ %13d ║%n", "최대 동시 요청 수",
                rl.maxConcurrentRequests(), q.maxConcurrentRequests());
        System.out.printf("║ %-32s ║ %13d ║ %13d ║%n", "재시도 횟수",
                rl.retryCount(), q.retryCount());
        System.out.printf("║ %-32s ║ %13d ║ %13d ║%n", "처리 완료 시간 (가상 ms)",
                rl.totalDurationMs(), q.totalDurationMs());
        System.out.println("╚══════════════════════════════════╩═══════════════╩═══════════════╝");
        System.out.println();
        System.out.println("[해석] Rate Limiting: 거절 → 즉시 재시도 → 요청 폭증 (Thundering Herd)");
        System.out.println("       Queue:         거절 없음 → 제어된 폴링 → 서버 부하 안정적");
        System.out.println("       ※ Queue는 처리 완료 시간이 더 길다 — 속도 vs 안정성 트레이드오프");
        System.out.println();
    }
}
