package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.behavior.event.BehaviorActionType;
import com.loopers.application.behavior.event.BehaviorLoggedEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BehaviorOutboxEventHandlerTest {

    @Test
    @DisplayName("좋아요 등록 행동 이벤트를 outbox row로 저장한다")
    void handle_likeRegister_savesOutbox() {
        OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
        BehaviorOutboxEventHandler behaviorOutboxEventHandler =
                new BehaviorOutboxEventHandler(outboxEventRepository, new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(behaviorOutboxEventHandler, "productMetricsTopic", "commerce.product.metrics.v1");

        BehaviorLoggedEvent event = new BehaviorLoggedEvent(
                UUID.randomUUID(),
                BehaviorActionType.LIKE_REGISTER,
                "member-1",
                "product-1",
                "",
                1L,
                Instant.now().toEpochMilli(),
                Instant.now()
        );

        behaviorOutboxEventHandler.handle(event);

        verify(outboxEventRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }
}
