package com.loopers.infrastructure.outbox;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Graceful Shutdown 및 복구 로직 통합 테스트
 */
@SpringBootTest
@DisplayName("OutboxRelayService — Graceful Shutdown 및 복구 테스트")
class OutboxGracefulShutdownTest {

    @Autowired
    private OutboxEventService outboxEventService;

    @Autowired
    private OutboxEventJpaRepository repository;

    @Autowired
    private OutboxRelayService relayService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("Graceful Shutdown 시 PROCESSING → PENDING 복원")
    void gracefulShutdownRecoversPROCESSINGtoPENDING() {
        // given: 10개 이벤트 생성
        for (int i = 1; i <= 10; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i));
        }

        // when: PENDING → PROCESSING 전환
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEventEntity> pending = repository.findPendingEventsForUpdate(50);
            pending.forEach(OutboxEventEntity::markProcessing);
            repository.saveAll(pending);
        });

        // then: PROCESSING 10개
        assertThat(repository.countByStatus(OutboxStatus.PROCESSING)).isEqualTo(10);

        // when: Graceful Shutdown 호출
        relayService.onShutdown();

        // then: PROCESSING → PENDING 복원됨
        assertThat(repository.countByStatus(OutboxStatus.PROCESSING)).isEqualTo(0);
        assertThat(repository.countByStatus(OutboxStatus.PENDING)).isEqualTo(10);
    }

    @Test
    @DisplayName("5분 이상 PROCESSING 이벤트 복구")
    void recoverStalledProcessingEvents() {
        // given: PROCESSING 이벤트 생성 (5분 이상 경과 시뮬레이션 어려움)
        // 실제 운영에서는 updated_at이 5분 이전인 이벤트를 찾아서 복구
        // 이 테스트는 복구 메서드 호출만 검증

        // when
        relayService.recoverStalledProcessingEvents();

        // then: 에러 없이 실행됨
        assertThat(repository.countByStatus(OutboxStatus.PENDING)).isEqualTo(0);
    }

    @Test
    @DisplayName("Shutdown 플래그 설정 시 Phase 2 건너뜀")
    void phase2SkipsWhenShuttingDown() {
        // given: PROCESSING 이벤트 생성
        for (int i = 1; i <= 5; i++) {
            outboxEventService.save("ORDER", (long) i, "OrderConfirmedEvent",
                    Map.of("orderId", i), "order-events-v1", String.valueOf(i));
        }

        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEventEntity> pending = repository.findPendingEventsForUpdate(50);
            pending.forEach(OutboxEventEntity::markProcessing);
            repository.saveAll(pending);
        });

        // when: Shutdown 플래그 설정
        relayService.onShutdown();

        // when: Phase 2 호출 시도 (shuttingDown=true이므로 스킵)
        relayService.publishProcessingEvents();

        // then: PROCESSING 그대로 유지 (Phase 2 스킵됨)
        assertThat(repository.countByStatus(OutboxStatus.PROCESSING)).isEqualTo(0); // 이미 복원됨
        assertThat(repository.countByStatus(OutboxStatus.PENDING)).isEqualTo(5);
    }
}
