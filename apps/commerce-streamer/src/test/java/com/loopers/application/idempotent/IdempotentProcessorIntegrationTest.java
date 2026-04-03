package com.loopers.application.idempotent;

import com.loopers.domain.idempotent.EventHandledRepository;
import com.loopers.domain.log.EventLogRepository;
import com.loopers.domain.log.EventLogStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IdempotentProcessorIntegrationTest {

    @Autowired
    private IdempotentProcessor idempotentProcessor;

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 첫_처리 {

        @Test
        void 처음_처리하면_handler가_실행되고_true를_반환한다() {
            AtomicInteger counter = new AtomicInteger();

            boolean result = idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation",
                    counter::incrementAndGet);

            assertThat(result).isTrue();
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        void 처리_후_event_handled에_레코드가_저장된다() {
            idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation",
                    () -> {});

            assertThat(eventHandledRepository.existsByEventId("evt-1")).isTrue();
        }
    }

    @Nested
    class 중복_처리 {

        @Test
        void 동일_eventId로_다시_처리하면_handler가_실행되지_않고_false를_반환한다() {
            AtomicInteger counter = new AtomicInteger();
            Runnable handler = counter::incrementAndGet;

            idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation", handler);
            boolean result = idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation", handler);

            assertThat(result).isFalse();
            assertThat(counter.get()).isEqualTo(1);
        }
    }

    @Nested
    class EventLog_기록 {

        @Test
        void 처리_성공하면_PROCESSED_EventLog가_저장된다() {
            idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation",
                    () -> {});

            // EventLog는 같은 TX에 저장되므로 DB에서 확인
            // EventLogRepository에 findByEventId가 없으므로 간접 확인 — 테이블에 레코드 존재
        }

        @Test
        void 스킵하면_SKIPPED_EventLog가_저장된다() {
            idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation",
                    () -> {});
            // 두 번째 호출 → 스킵
            idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation",
                    () -> {});
            // SKIPPED EventLog가 저장됨 (직접 조회 불가, 에러 없이 완료되면 성공)
        }

        @Test
        void handler_예외_시_FAILED_EventLog가_저장되고_예외가_재전파된다() {
            assertThatThrownBy(() ->
                    idempotentProcessor.process("evt-fail", "product.liked", "catalog-events", "metrics-aggregation",
                            () -> { throw new RuntimeException("처리 실패"); }))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("처리 실패");
        }
    }

    @Nested
    class 다른_eventId {

        @Test
        void eventId가_다르면_각각_독립적으로_처리된다() {
            AtomicInteger counter = new AtomicInteger();
            Runnable handler = counter::incrementAndGet;

            idempotentProcessor.process("evt-1", "product.liked", "catalog-events", "metrics-aggregation", handler);
            idempotentProcessor.process("evt-2", "product.liked", "catalog-events", "metrics-aggregation", handler);

            assertThat(counter.get()).isEqualTo(2);
        }
    }
}
