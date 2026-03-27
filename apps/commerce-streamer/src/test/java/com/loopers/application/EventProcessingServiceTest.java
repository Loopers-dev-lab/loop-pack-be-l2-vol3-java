package com.loopers.application;

import com.loopers.infrastructure.EventHandledRepository;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.ProductLikedEventPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventProcessingServiceTest {

    private EventHandledRepository eventHandledRepository;
    private EventProcessingService eventProcessingService;
    private AtomicInteger handleCount;

    @BeforeEach
    void setUp() {
        eventHandledRepository = mock(EventHandledRepository.class);
        handleCount = new AtomicInteger(0);

        EventHandler<ProductLikedEventPayload> handler = new EventHandler<>() {
            @Override
            public boolean supports(Event<EventPayload> event) {
                return event.getType() == EventType.PRODUCT_LIKED;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handle(Event<ProductLikedEventPayload> event) {
                handleCount.incrementAndGet();
            }
        };

        eventProcessingService = new EventProcessingService(List.of(handler), eventHandledRepository);
    }

    @DisplayName("이벤트를 처리하면 핸들러가 실행된다")
    @Test
    void executes_handler_for_matching_event() {
        // arrange
        Event<EventPayload> event = Event.of(1L, EventType.PRODUCT_LIKED, ProductLikedEventPayload.of(100L, 1L));

        // act
        eventProcessingService.process(event);

        // assert
        assertThat(handleCount.get()).isEqualTo(1);
        verify(eventHandledRepository).save(any());
    }

    @DisplayName("이미 처리된 이벤트는 핸들러를 실행하지 않는다")
    @Test
    void skips_already_handled_event() {
        // arrange
        Event<EventPayload> event = Event.of(1L, EventType.PRODUCT_LIKED, ProductLikedEventPayload.of(100L, 1L));
        when(eventHandledRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        // act
        eventProcessingService.process(event);

        // assert
        assertThat(handleCount.get()).isEqualTo(0);
    }
}
