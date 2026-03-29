package com.loopers.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventHandledTest {

    @DisplayName("create: topic과 eventId로 EventHandled를 생성한다")
    @Test
    void create() {
        EventHandled handled = EventHandled.create("catalog.events.topic-v1", "uuid-123");

        assertThat(handled.getTopic()).isEqualTo("catalog.events.topic-v1");
        assertThat(handled.getEventId()).isEqualTo("uuid-123");
    }
}
