package com.loopers.infrastructure.outbox;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox Relay E2E 성능 테스트 — Phase 1 + Phase 2 (실제 Kafka 발행)
 *
 * 전제:
 * - Docker Kafka가 localhost:19092에서 실행 중이어야 함
 * - docker compose -f ./docker/infra-compose.yml up -d
 */
@SpringBootTest
@DisplayName("Outbox Relay — E2E 성능 테스트")
class OutboxRelayPerformanceE2ETest {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayPerformanceE2ETest.class);

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventJpaRepository repository;

    @Autowired
    private OutboxRelayService relayService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledProcessor;

    @BeforeEach
    void setUp() {
        // 스케줄러 비활성화 — 테스트와 @Scheduled 메서드 충돌 방지
        scheduledProcessor.destroy();
        databaseCleanUp.truncateAllTables();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("시나리오 1: Relay 기준선 — 1000건 Phase 1 + Phase 2")
    void scenario1_relay_baseline_1000_events() {
        int totalEvents = 1000;

        // Given: 1000건 INSERT (10 partition key x 100건)
        long insertStart = System.nanoTime();
        for (int pk = 0; pk < 10; pk++) {
            for (int i = 0; i < 100; i++) {
                outboxEventService.save(
                        "PRODUCT", (long) pk,
                        "TestEvent", Map.of("pk", pk, "seq", i),
                        "catalog-events-v1", String.valueOf(pk)
                );
            }
        }
        long insertDuration = (System.nanoTime() - insertStart) / 1_000_000;

        assertThat(repository.countByStatus(OutboxStatus.PENDING)).isEqualTo(totalEvents);
        log.info("=== 시나리오 1: Relay 기준선 ===");
        log.info("1000건 INSERT: {}ms", insertDuration);

        // Phase 1: PENDING → PROCESSING
        List<Long> phase1Times = new ArrayList<>();
        int phase1Batch = 0;
        while (repository.countByStatus(OutboxStatus.PENDING) > 0) {
            phase1Batch++;
            long start = System.nanoTime();
            relayService.markPendingAsProcessing();
            long duration = (System.nanoTime() - start) / 1_000_000;
            phase1Times.add(duration);
        }
        long phase1Total = phase1Times.stream().mapToLong(Long::longValue).sum();

        // Phase 2: PROCESSING → Kafka 발행 → PUBLISHED
        List<Long> phase2Times = new ArrayList<>();
        int phase2Batch = 0;
        while (repository.countByStatus(OutboxStatus.PROCESSING) > 0) {
            phase2Batch++;
            long start = System.nanoTime();
            relayService.publishProcessingEvents();
            long duration = (System.nanoTime() - start) / 1_000_000;
            phase2Times.add(duration);
        }
        long phase2Total = phase2Times.stream().mapToLong(Long::longValue).sum();

        // 결과 출력
        log.info("--- Phase 1 ---");
        for (int i = 0; i < phase1Times.size(); i++) {
            log.info("Phase 1 ({}차): {}ms (500건)", i + 1, phase1Times.get(i));
        }
        log.info("Phase 1 합계: {}ms", phase1Total);

        log.info("--- Phase 2 ---");
        for (int i = 0; i < phase2Times.size(); i++) {
            log.info("Phase 2 ({}차): {}ms (500건)", i + 1, phase2Times.get(i));
        }
        log.info("Phase 2 합계: {}ms", phase2Total);

        long total = phase1Total + phase2Total;
        double throughput = (double) totalEvents / total * 1000;
        long published = repository.countByStatus(OutboxStatus.PUBLISHED);

        log.info("--- 종합 ---");
        log.info("전체: {}ms", total);
        log.info("실제 처리량: {} events/sec", String.format("%.1f", throughput));
        log.info("PUBLISHED: {}건", published);

        // 검증
        assertThat(published).isEqualTo(totalEvents);
        assertThat(total).as("Phase 1 + Phase 2 합계가 2초 미만이어야 함").isLessThan(2000);
    }

    @Test
    @DisplayName("시나리오 2: burst 5000건 — Relay 소화 시간")
    void scenario2_burst_5000_events() {
        int totalEvents = 5000;

        // Given: 5000건 한꺼번에 INSERT
        long insertStart = System.nanoTime();
        for (int i = 0; i < totalEvents; i++) {
            outboxEventService.save(
                    "COUPON", (long) (i % 10),
                    "CouponIssueRequestedEvent", Map.of("couponId", i),
                    "coupon-issue-requests-v1", String.valueOf(i % 10)
            );
        }
        long insertDuration = (System.nanoTime() - insertStart) / 1_000_000;

        log.info("=== 시나리오 2: burst 5000건 ===");
        log.info("5000건 INSERT: {}ms", insertDuration);

        // When: Phase 1 + Phase 2 반복
        long relayStart = System.nanoTime();
        int cycles = 0;

        while (repository.countByStatus(OutboxStatus.PENDING) > 0
                || repository.countByStatus(OutboxStatus.PROCESSING) > 0) {
            relayService.markPendingAsProcessing();
            relayService.publishProcessingEvents();
            cycles++;

            long pending = repository.countByStatus(OutboxStatus.PENDING);
            long processing = repository.countByStatus(OutboxStatus.PROCESSING);
            log.info("Cycle {}: PENDING={}, PROCESSING={}", cycles, pending, processing);
        }

        long totalDuration = (System.nanoTime() - relayStart) / 1_000_000;
        long published = repository.countByStatus(OutboxStatus.PUBLISHED);
        double throughput = (double) totalEvents / totalDuration * 1000;

        log.info("--- 종합 ---");
        log.info("전체 소화: {}ms, {} cycles", totalDuration, cycles);
        log.info("실제 처리량: {} events/sec", String.format("%.1f", throughput));
        log.info("PUBLISHED: {}건", published);

        // 검증
        assertThat(published).isEqualTo(totalEvents);
    }
}
