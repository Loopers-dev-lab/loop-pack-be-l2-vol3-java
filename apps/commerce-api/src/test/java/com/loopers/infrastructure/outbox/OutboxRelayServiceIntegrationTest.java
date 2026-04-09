package com.loopers.infrastructure.outbox;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxRelayService 통합 테스트 — 멀티 스레드 중복 방지 검증
 *
 * 핵심 검증 사항:
 * 1. FOR UPDATE SKIP LOCKED가 멀티 스레드 환경에서 중복 조회를 방지하는가?
 * 2. 2단계 Relay가 순차적으로 동작하는가?
 * 3. Partition Key별 순서가 보장되는가?
 *
 * @Scheduled Relay 스케줄러(1초 간격)가 테스트 데이터를 먼저 처리하는 경합을 방지하기 위해
 * BeforeEach에서 스케줄러를 비활성화한다.
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxRelayServiceIntegrationTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduledProcessor;

    @BeforeEach
    void setUp() {
        scheduledProcessor.destroy();
        databaseCleanUp.truncateAllTables();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("멀티 스레드 환경에서 FOR UPDATE SKIP LOCKED가 중복 조회를 방지한다")
    void for_update_skip_locked_prevents_duplicate_acquisition() throws InterruptedException {
        // given: 100개의 PENDING 이벤트 생성
        for (int i = 1; i <= 100; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i));
        }

        // when: 4개의 스레드가 동시에 PENDING → PROCESSING 전환 시도
        int threadCount = 4;
        int batchSize = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Map<Integer, List<Long>> acquiredEventsByThread = new ConcurrentHashMap<>();

        for (int threadId = 0; threadId < threadCount; threadId++) {
            int finalThreadId = threadId;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드 동시 시작
                    List<Long> acquired = transactionTemplate.execute(status -> {
                        List<OutboxEventEntity> events =
                                outboxEventJpaRepository.findPendingEventsForUpdate(batchSize);
                        events.forEach(OutboxEventEntity::markProcessing);
                        outboxEventJpaRepository.saveAll(events);
                        return events.stream().map(OutboxEventEntity::getId).toList();
                    });
                    acquiredEventsByThread.put(finalThreadId, acquired != null ? acquired : List.of());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 조회된 이벤트에 중복이 없어야 함 (SKIP LOCKED 핵심 검증)
        List<Long> allAcquiredIds = acquiredEventsByThread.values().stream()
                .flatMap(List::stream)
                .toList();

        assertThat(allAcquiredIds).doesNotHaveDuplicates(); // 중복 없음 — SKIP LOCKED 핵심

        // PROCESSING 전환된 수 = 조회된 수 (누락 없음)
        long processingCount = outboxEventJpaRepository.countByStatus(OutboxStatus.PROCESSING);
        assertThat(processingCount).isEqualTo(allAcquiredIds.size());

        // 전체(PENDING + PROCESSING) = 100 (데이터 유실 없음)
        long totalCount = outboxEventJpaRepository.countByStatus(OutboxStatus.PENDING) + processingCount;
        assertThat(totalCount).isEqualTo(100);
    }

    @Test
    @DisplayName("Phase 1과 Phase 2가 순차적으로 동작한다")
    void two_phase_relay_works_sequentially() {
        // given: 10개의 PENDING 이벤트 생성
        for (int i = 1; i <= 10; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i));
        }

        // when: Phase 1 실행 (PENDING → PROCESSING)
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEventEntity> pending = outboxEventJpaRepository.findPendingEventsForUpdate(50);
            pending.forEach(OutboxEventEntity::markProcessing);
            outboxEventJpaRepository.saveAll(pending);
        });

        // then: PROCESSING 상태 확인
        long processingCount = outboxEventJpaRepository.countByStatus(OutboxStatus.PROCESSING);
        assertThat(processingCount).isEqualTo(10);

        // when: Phase 2 실행 (PROCESSING → PUBLISHED 시뮬레이션)
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEventEntity> processing = outboxEventJpaRepository.findProcessingEvents(50);
            processing.forEach(OutboxEventEntity::markPublished);
            outboxEventJpaRepository.saveAll(processing);
        });

        // then: PUBLISHED 상태 확인
        long publishedCount = outboxEventJpaRepository.countByStatus(OutboxStatus.PUBLISHED);
        assertThat(publishedCount).isEqualTo(10);
    }

    @Test
    @DisplayName("동일 Partition Key는 순차 처리되어야 한다")
    void same_partition_key_processed_sequentially() {
        // given: 같은 Partition Key로 5개 이벤트 생성
        String partitionKey = "order-123";
        List<Long> eventIds = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            OutboxEventEntity event = outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i, "seq", i), "order-events-v1", partitionKey);
            eventIds.add(event.getId());
        }

        // when: PENDING → PROCESSING 전환
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEventEntity> pending = outboxEventJpaRepository.findPendingEventsForUpdate(50);
            pending.forEach(OutboxEventEntity::markProcessing);
            outboxEventJpaRepository.saveAll(pending);
        });

        // then: PROCESSING 이벤트를 조회하면 생성 순서대로 나와야 함
        List<OutboxEventEntity> processing = outboxEventJpaRepository.findProcessingEvents(50);
        assertThat(processing).hasSize(5);

        for (int i = 0; i < 5; i++) {
            assertThat(processing.get(i).getId()).isEqualTo(eventIds.get(i));
            assertThat(processing.get(i).getPartitionKey()).isEqualTo(partitionKey);
        }
    }

    @Test
    @DisplayName("LIMIT보다 많은 이벤트가 있을 때 LIMIT만큼만 조회된다")
    void respects_limit_when_more_events_exist() {
        // given: 100개의 PENDING 이벤트 생성
        for (int i = 1; i <= 100; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i));
        }

        // when: LIMIT 30으로 조회
        List<OutboxEventEntity> result = transactionTemplate.execute(status ->
                outboxEventJpaRepository.findPendingEventsForUpdate(30)
        );

        // then: 정확히 30개만 조회됨
        assertThat(result).hasSize(30);
    }

    @Test
    @DisplayName("멀티 스레드 환경에서 각 스레드는 서로 다른 이벤트를 획득한다")
    void concurrent_threads_acquire_different_events() throws InterruptedException {
        // given: 200개의 PENDING 이벤트 생성
        for (int i = 1; i <= 200; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i));
        }

        // when: 5개의 스레드가 동시에 50개씩 조회
        int threadCount = 5;
        int batchSize = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        Map<Integer, Integer> acquiredCountByThread = new ConcurrentHashMap<>();
        AtomicInteger totalAcquired = new AtomicInteger(0);

        for (int threadId = 0; threadId < threadCount; threadId++) {
            int finalThreadId = threadId;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드 동시 시작
                    Integer count = transactionTemplate.execute(status -> {
                        List<OutboxEventEntity> events =
                                outboxEventJpaRepository.findPendingEventsForUpdate(batchSize);
                        events.forEach(OutboxEventEntity::markProcessing);
                        outboxEventJpaRepository.saveAll(events);
                        return events.size();
                    });
                    acquiredCountByThread.put(finalThreadId, count != null ? count : 0);
                    totalAcquired.addAndGet(count != null ? count : 0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 시작
        endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 중복 없이 조회됨 (SKIP LOCKED 핵심 검증)
        // TX 직렬화 특성상 동시 접근 시 각 스레드가 획득하는 수는 환경에 따라 달라질 수 있지만,
        // 중복은 절대 발생하지 않아야 한다.
        long processingCount = outboxEventJpaRepository.countByStatus(OutboxStatus.PROCESSING);
        long pendingCount = outboxEventJpaRepository.countByStatus(OutboxStatus.PENDING);

        // 데이터 유실 없음
        assertThat(processingCount + pendingCount).isEqualTo(200);

        // 조회된 총 수 = PROCESSING 전환된 수
        assertThat(totalAcquired.get()).isEqualTo((int) processingCount);
    }
}
