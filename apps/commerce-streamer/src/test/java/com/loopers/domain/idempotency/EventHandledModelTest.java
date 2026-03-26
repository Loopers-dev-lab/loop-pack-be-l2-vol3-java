package com.loopers.domain.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EventHandledModel 도메인 모델 테스트")
class EventHandledModelTest {

    @Test
    @DisplayName("생성 시 eventId와 handledAt이 설정된다")
    void create_ShouldSetEventIdAndHandledAt() {
        EventHandledModel model = new EventHandledModel(12345L);

        assertThat(model.getEventId()).isEqualTo(12345L);
        assertThat(model.getHandledAt()).isNotNull();
    }

    @Test
    @DisplayName("서로 다른 eventId로 생성한 모델은 다른 ID를 가진다")
    void create_WithDifferentIds_ShouldHaveDifferentEventIds() {
        EventHandledModel model1 = new EventHandledModel(1L);
        EventHandledModel model2 = new EventHandledModel(2L);

        assertThat(model1.getEventId()).isNotEqualTo(model2.getEventId());
    }
}
