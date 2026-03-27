package com.loopers.infrastructure.outbox;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox Relay 성능 테스트
 *
 * 목적:
 * 1. 실제 처리량 측정 (건/초)
 * 2. Phase 1/2 처리 시간 측정
 * 3. 이론값 vs 실측값 비교
 */
@SpringBootTest
@DisplayName("OutboxRelayService — 성능 테스트")
class OutboxPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(OutboxPerformanceTest.class);

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventJpaRepository repository;

    @Autowired
    private OutboxRelayService relayService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("1000건 이벤트 처리 성능 측정")
    void measure_throughput_for_1000_events() throws InterruptedException {
        // given: 1000개 이벤트 생성
        int totalEvents = 1000;
        log.info("=== 1000건 이벤트 생성 시작 ===");
        long createStart = System.currentTimeMillis();

        for (int i = 1; i <= totalEvents; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i % 10)); // 10개 파티션
        }

        long createDuration = System.currentTimeMillis() - createStart;
        log.info("이벤트 생성 완료: {}ms", createDuration);

        // when: Relay 실행 (Phase 1 + Phase 2 반복)
        log.info("=== Relay 시작 ===");
        long relayStart = System.currentTimeMillis();

        int maxIterations = 100; // 최대 100번 (100초)
        int iteration = 0;

        while (iteration < maxIterations) {
            // Phase 1: PENDING → PROCESSING
            long phase1Start = System.currentTimeMillis();
            relayService.markPendingAsProcessing();
            long phase1Duration = System.currentTimeMillis() - phase1Start;

            // Phase 2: PROCESSING → Kafka (실제로는 Kafka 없이 상태만 변경)
            long phase2Start = System.currentTimeMillis();
            // relayService.publishProcessingEvents(); // Kafka 없으면 주석 처리
            long phase2Duration = System.currentTimeMillis() - phase2Start;

            long pendingCount = repository.countByStatus(OutboxStatus.PENDING);
            long processingCount = repository.countByStatus(OutboxStatus.PROCESSING);

            if (iteration % 10 == 0) {
                log.info("Iteration {}: Phase1={}ms, Phase2={}ms, PENDING={}, PROCESSING={}",
                        iteration, phase1Duration, phase2Duration, pendingCount, processingCount);
            }

            // PENDING이 0이면 종료
            if (pendingCount == 0 && processingCount >= totalEvents) {
                break;
            }

            iteration++;
            TimeUnit.MILLISECONDS.sleep(100); // 0.1초 대기 (실제는 1초)
        }

        long relayEnd = System.currentTimeMillis();
        long totalDuration = relayEnd - relayStart;

        // then: 결과 측정
        long processingCount = repository.countByStatus(OutboxStatus.PROCESSING);

        double throughputPerSecond = (double) processingCount / (totalDuration / 1000.0);
        double avgIterationTime = (double) totalDuration / iteration;

        log.info("=== 성능 측정 결과 ===");
        log.info("총 이벤트: {}", totalEvents);
        log.info("처리된 이벤트: {}", processingCount);
        log.info("총 소요 시간: {}ms ({}초)", totalDuration, totalDuration / 1000.0);
        log.info("총 반복 횟수: {}", iteration);
        log.info("평균 반복 시간: {}ms", avgIterationTime);
        log.info("실측 처리량: {} 건/초", throughputPerSecond);
        log.info("이론 처리량: 500건/초 (1초 × 500건 배치)");

        assertThat(processingCount).isGreaterThanOrEqualTo((long) (totalEvents * 0.9)); // 90% 이상 처리
    }

    @Test
    @DisplayName("Phase 1 처리 시간 측정 (배치 500건)")
    void measure_phase1_duration() {
        // given: 500개 이벤트 생성
        for (int i = 1; i <= 500; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i));
        }

        // when: Phase 1 실행 10회
        long totalDuration = 0;
        int iterations = 10;

        for (int i = 0; i < iterations; i++) {
            long start = System.currentTimeMillis();
            relayService.markPendingAsProcessing();
            long duration = System.currentTimeMillis() - start;
            totalDuration += duration;

            log.info("Phase 1 - Iteration {}: {}ms", i + 1, duration);
        }

        // then: 평균 시간 계산
        double avgDuration = (double) totalDuration / iterations;
        log.info("=== Phase 1 평균 처리 시간 ===");
        log.info("평균: {}ms", avgDuration);
        log.info("최대: {}ms (단일 측정)", totalDuration / iterations);

        assertThat(avgDuration).isLessThan(1000); // 1초 이내
    }

    @Test
    @DisplayName("배치 크기별 처리 시간 비교")
    void compare_batch_sizes() {
        int[] batchSizes = {50, 100, 200, 500};

        log.info("=== 배치 크기별 처리 시간 비교 ===");

        for (int batchSize : batchSizes) {
            databaseCleanUp.truncateAllTables();

            // given: batchSize만큼 이벤트 생성
            for (int i = 1; i <= batchSize; i++) {
                outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                        Map.of("orderId", i), "order-events-v1", String.valueOf(i));
            }

            // when: Phase 1 실행
            long start = System.currentTimeMillis();
            relayService.markPendingAsProcessing();
            long duration = System.currentTimeMillis() - start;

            long processingCount = repository.countByStatus(OutboxStatus.PROCESSING);

            log.info("배치 크기: {}, 처리 시간: {}ms, 처리된 이벤트: {}",
                    batchSize, duration, processingCount);
        }
    }
}
