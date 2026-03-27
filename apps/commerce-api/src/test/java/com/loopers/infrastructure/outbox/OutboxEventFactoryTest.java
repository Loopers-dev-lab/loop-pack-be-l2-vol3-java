package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventStatus;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxEventFactoryTest {

    private final OutboxEventFactory factory = new OutboxEventFactory(new ObjectMapper());

    @Nested
    class 생성 {

        @Test
        void 유효한_페이로드면_OutboxEvent가_생성된다() {
            record TestPayload(String name) {}

            OutboxEvent event = factory.create("test.event", "Test", "1", "test-topic", new TestPayload("hello"));

            assertAll(
                    () -> assertThat(event.getEventType()).isEqualTo("test.event"),
                    () -> assertThat(event.getAggregateType()).isEqualTo("Test"),
                    () -> assertThat(event.getAggregateId()).isEqualTo("1"),
                    () -> assertThat(event.getTopic()).isEqualTo("test-topic"),
                    () -> assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING)
            );
        }

        @Test
        void 생성된_eventId가_UUID_형식이다() {
            OutboxEvent event = factory.create("test.event", "Test", "1", "test-topic", "payload");

            assertThat(event.getEventId()).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        }

        @Test
        void payload가_JSON_문자열로_직렬화된다() {
            record TestPayload(Long id, String name) {}

            OutboxEvent event = factory.create("test.event", "Test", "1", "test-topic", new TestPayload(1L, "hello"));

            assertThat(event.getPayload()).contains("\"id\":1").contains("\"name\":\"hello\"");
        }
    }

    @Nested
    class 직렬화_실패 {

        @Test
        void 직렬화_불가능한_객체면_IllegalArgumentException이_발생한다() {
            Object unserializable = new Object() {
                public Object getSelf() { return this; } // 순환 참조
            };

            assertThatThrownBy(() -> factory.create("test.event", "Test", "1", "test-topic", unserializable))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("직렬화 실패");
        }
    }
}
