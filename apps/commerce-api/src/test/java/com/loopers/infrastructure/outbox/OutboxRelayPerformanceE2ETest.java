package com.loopers.infrastructure.outbox;

import com.loopers.infrastructure.event.EventHandledEntity;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Outbox Relay E2E 성능 테스트 — Phase 1 + Phase 2 (실제 Kafka 발행)
 *
 * 전제:
 * - Docker Kafka가 localhost:19092에서 실행 중이어야 함
 * - docker compose -f ./docker/infra-compose.yml up -d
 */
@SpringBootTest
@TestPropertySource(properties = {
        "kafka.topic.catalog-events.name=catalog-events-v1",
        "kafka.topic.catalog-events.partitions=3",
        "kafka.topic.catalog-events.replicas=1",
        "kafka.topic.catalog-events.min-insync-replicas=1",
        "kafka.topic.order-events.name=order-events-v1",
        "kafka.topic.order-events.partitions=3",
        "kafka.topic.order-events.replicas=1",
        "kafka.topic.order-events.min-insync-replicas=1",
        "kafka.topic.coupon-issue-requests.name=coupon-issue-requests-v1",
        "kafka.topic.coupon-issue-requests.partitions=3",
        "kafka.topic.coupon-issue-requests.replicas=1",
        "kafka.topic.coupon-issue-requests.min-insync-replicas=1",
        "kafka.topic.user-activity-events.name=user-activity-events-v1",
        "kafka.topic.user-activity-events.partitions=3",
        "kafka.topic.user-activity-events.replicas=1",
        "kafka.topic.user-activity-events.min-insync-replicas=1",
        "kafka.topic.user-activity-events.retention-ms=259200000",
        "kafka.topic.pipeline-dlq.name=pipeline-dlq-v1",
        "kafka.topic.pipeline-dlq.partitions=1",
        "kafka.topic.pipeline-dlq.replicas=1",
        "kafka.topic.pipeline-dlq.min-insync-replicas=1",
        "kafka.topic.pipeline-dlq.retention-ms=2592000000"
})
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
    private EventHandledJpaRepository eventHandledRepository;

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

    @Test
    @DisplayName("시나리오 3: Kafka 지연 시 Phase 2 영향 — 5분 threshold 도달 조건")
    void scenario3_kafka_latency_phase2_impact() {
        // KafkaTemplate을 spy로 감싸서 send()에 지연 주입
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> originalTemplate =
                (KafkaTemplate<Object, Object>) ReflectionTestUtils.getField(relayService, "kafkaTemplate");
        KafkaTemplate<Object, Object> spyTemplate = Mockito.spy(originalTemplate);

        // 건당 지연 시뮬레이션: Case별로 실행
        int[] delaysMs = {0, 50, 200};
        for (int delayMs : delaysMs) {
            databaseCleanUp.truncateAllTables();

            // 500건 INSERT (1배치)
            for (int pk = 0; pk < 10; pk++) {
                for (int i = 0; i < 50; i++) {
                    outboxEventService.save(
                            "PRODUCT", (long) pk,
                            "TestEvent", Map.of("pk", pk, "seq", i),
                            "catalog-events-v1", String.valueOf(pk)
                    );
                }
            }

            // 지연 주입
            if (delayMs > 0) {
                doAnswer(invocation -> {
                    Thread.sleep(delayMs);
                    return invocation.callRealMethod();
                }).when(spyTemplate).send(any(ProducerRecord.class));
                ReflectionTestUtils.setField(relayService, "kafkaTemplate", spyTemplate);
            } else {
                ReflectionTestUtils.setField(relayService, "kafkaTemplate", originalTemplate);
            }

            // Phase 1
            relayService.markPendingAsProcessing();

            // Phase 2 측정
            long phase2Start = System.nanoTime();
            relayService.publishProcessingEvents();
            long phase2Duration = (System.nanoTime() - phase2Start) / 1_000_000;

            long published = repository.countByStatus(OutboxStatus.PUBLISHED);
            long failed = repository.countByStatus(OutboxStatus.FAILED);

            log.info("=== 시나리오 3: 건당 {}ms 지연 ===", delayMs);
            log.info("Phase 2 소요: {}ms (500건)", phase2Duration);
            log.info("Phase 2 소요: {}초", String.format("%.1f", phase2Duration / 1000.0));
            log.info("PUBLISHED: {}건, FAILED: {}건", published, failed);
            log.info("5분(300초) 대비: {}%", String.format("%.1f", phase2Duration / 3000.0));

            // 500건 burst에서 건당 200ms면 → parallelStream 병렬도에 따라 달라짐
            // commonPool 크기 = CPU-1. 10개 partition key면 병렬도 ~10이지만 commonPool 제한
        }

        // 원본 복원
        ReflectionTestUtils.setField(relayService, "kafkaTemplate", originalTemplate);
    }

    @Test
    @DisplayName("시나리오 4: Consumer 멱등성 오버헤드 — event_handled SELECT + INSERT 비용")
    void scenario4_consumer_idempotency_overhead() {
        int totalEvents = 1000;

        log.info("=== 시나리오 4: Consumer 멱등성 오버헤드 측정 ===");

        // Case A: event_handled가 비어있을 때 existsByEventId (miss) + save
        long checkMissTotal = 0;
        long saveTotal = 0;

        for (int i = 0; i < totalEvents; i++) {
            String eventId = "perf-test-event-" + i;

            // existsByEventId (MISS — 존재하지 않음)
            long checkStart = System.nanoTime();
            boolean exists = eventHandledRepository.existsByEventId(eventId);
            long checkDuration = System.nanoTime() - checkStart;
            checkMissTotal += checkDuration;

            assertThat(exists).isFalse();

            // save (INSERT)
            long saveStart = System.nanoTime();
            eventHandledRepository.save(EventHandledEntity.of(eventId, "catalog-events-v1"));
            long saveDuration = System.nanoTime() - saveStart;
            saveTotal += saveDuration;
        }

        double avgCheckMissMs = (checkMissTotal / 1_000_000.0) / totalEvents;
        double avgSaveMs = (saveTotal / 1_000_000.0) / totalEvents;

        log.info("--- Case A: 신규 이벤트 (MISS → INSERT) ---");
        log.info("existsByEventId (MISS) 평균: {}ms/건", String.format("%.3f", avgCheckMissMs));
        log.info("save (INSERT) 평균: {}ms/건", String.format("%.3f", avgSaveMs));
        log.info("멱등성 오버헤드 합계 (MISS+INSERT): {}ms/건", String.format("%.3f", avgCheckMissMs + avgSaveMs));
        log.info("1000건 기준 총 오버헤드: {}ms", String.format("%.1f", (checkMissTotal + saveTotal) / 1_000_000.0));

        // Case B: event_handled가 가득 찬 상태에서 existsByEventId (HIT)
        long checkHitTotal = 0;

        for (int i = 0; i < totalEvents; i++) {
            String eventId = "perf-test-event-" + i;

            long checkStart = System.nanoTime();
            boolean exists = eventHandledRepository.existsByEventId(eventId);
            long checkDuration = System.nanoTime() - checkStart;
            checkHitTotal += checkDuration;

            assertThat(exists).isTrue();
        }

        double avgCheckHitMs = (checkHitTotal / 1_000_000.0) / totalEvents;

        log.info("--- Case B: 중복 이벤트 (HIT → SKIP) ---");
        log.info("existsByEventId (HIT) 평균: {}ms/건", String.format("%.3f", avgCheckHitMs));
        log.info("1000건 기준 총 오버헤드: {}ms", String.format("%.1f", checkHitTotal / 1_000_000.0));

        // Case C: 테이블에 대량 레코드가 있을 때 성능 (UNIQUE 인덱스 효과)
        log.info("--- Case C: UNIQUE 인덱스 효과 ---");
        log.info("event_handled 레코드 수: {}건", eventHandledRepository.count());
        log.info("HIT 시 SKIP 비용(INSERT 없음): {}ms/건 — MISS 대비 {}% 절감",
                String.format("%.3f", avgCheckHitMs),
                String.format("%.1f", (1 - avgCheckHitMs / (avgCheckMissMs + avgSaveMs)) * 100));

        // 종합: Relay 처리량 대비 Consumer 멱등성 오버헤드 비율
        double relay95ThroughputMs = 1000.0 / 95.3; // 시나리오 1 기준 ~10.5ms/건
        double idempotencyOverheadMs = avgCheckMissMs + avgSaveMs;
        log.info("--- 종합 ---");
        log.info("Relay 처리 시간: {}ms/건 (95.3건/초 기준)", String.format("%.3f", relay95ThroughputMs));
        log.info("멱등성 오버헤드: {}ms/건", String.format("%.3f", idempotencyOverheadMs));
        log.info("오버헤드 비율: {}%", String.format("%.1f", idempotencyOverheadMs / relay95ThroughputMs * 100));
    }
}
