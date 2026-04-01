package com.loopers.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.EventOutbox;
import com.loopers.domain.event.EventOutboxRepository;
import com.loopers.domain.event.LikeCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventPublisherImplTest {

    private DomainEventPublisherImpl domainEventPublisher;
    private List<EventOutbox> savedOutboxes;
    private List<Object> publishedEvents;

    @BeforeEach
    void setUp() {
        savedOutboxes = new ArrayList<>();
        publishedEvents = new ArrayList<>();
        EventOutboxRepository eventOutboxRepository = outbox -> {
            savedOutboxes.add(outbox);
            return outbox;
        };
        ApplicationEventPublisher applicationEventPublisher = publishedEvents::add;
        domainEventPublisher = new DomainEventPublisherImpl(
            eventOutboxRepository, applicationEventPublisher, new ObjectMapper());
    }

    @DisplayName("publish 호출 시 EventOutbox가 저장되고 ApplicationEvent가 발행된다")
    @Test
    void publish_savesOutboxAndPublishesEvent() {
        Map<String, Object> payload = Map.of("productId", 1L, "memberId", 2L);
        LikeCreatedEvent event = new LikeCreatedEvent(1L, 2L);

        domainEventPublisher.publish("catalog", "1", "LIKE_CREATED", payload, event);

        assertThat(savedOutboxes).hasSize(1);
        EventOutbox outbox = savedOutboxes.get(0);
        assertThat(outbox.getAggregateType()).isEqualTo("catalog");
        assertThat(outbox.getAggregateId()).isEqualTo("1");
        assertThat(outbox.getEventType()).isEqualTo("LIKE_CREATED");
        assertThat(outbox.getPayload()).contains("productId");
        assertThat(outbox.getPayload()).contains("memberId");

        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0)).isInstanceOf(LikeCreatedEvent.class);
    }

    @DisplayName("직렬화 불가능한 payload 전달 시 RuntimeException이 발생한다")
    @Test
    void publish_withUnserializablePayload_throwsRuntimeException() {
        Object unserializable = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() { return this; }
        };

        assertThatThrownBy(() ->
            domainEventPublisher.publish("test", "1", "TEST", unserializable, new Object()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("이벤트 페이로드 직렬화 실패");
    }
}
