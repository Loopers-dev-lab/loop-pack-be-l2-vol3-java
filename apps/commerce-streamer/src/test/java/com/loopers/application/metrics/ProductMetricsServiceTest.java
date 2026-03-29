package com.loopers.application.metrics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.MetricsEventHandler;
import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;

@ExtendWith(MockitoExtension.class)
class ProductMetricsServiceTest {

    @InjectMocks
    private ProductMetricsService productMetricsService;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private MetricsEventHandler matchingHandler;

    @Mock
    private MetricsEventHandler nonMatchingHandler;

    @DisplayName("handleEvent를 호출할 때,")
    @Nested
    class HandleEvent {

        @DisplayName("신규 이벤트이면, 매칭되는 handler에 처리를 위임한다.")
        @Test
        void delegatesToMatchingHandler_whenNewEvent() {
            // arrange
            productMetricsService = new ProductMetricsService(
                    eventHandledRepository, List.of(nonMatchingHandler, matchingHandler));

            given(eventHandledRepository.markIfAbsent("event-1")).willReturn(true);
            given(nonMatchingHandler.supports(MetricsEventType.LIKED)).willReturn(false);
            given(matchingHandler.supports(MetricsEventType.LIKED)).willReturn(true);

            MetricsPayload.Like payload = new MetricsPayload.Like(1L, true);
            MetricsEventMeta meta = new MetricsEventMeta("event-1", MetricsEventType.LIKED);

            // act
            productMetricsService.handleEvent(meta, payload);

            // assert
            then(matchingHandler).should().handle(payload);
            then(nonMatchingHandler).should().supports(MetricsEventType.LIKED);
            then(nonMatchingHandler).shouldHaveNoMoreInteractions();
        }

        @DisplayName("중복 이벤트이면, handler를 호출하지 않는다.")
        @Test
        void skipsHandler_whenDuplicateEvent() {
            // arrange
            productMetricsService = new ProductMetricsService(
                    eventHandledRepository, List.of(matchingHandler));

            given(eventHandledRepository.markIfAbsent("dup-id")).willReturn(false);

            MetricsPayload.Like payload = new MetricsPayload.Like(1L, true);
            MetricsEventMeta meta = new MetricsEventMeta("dup-id", MetricsEventType.LIKED);

            // act
            productMetricsService.handleEvent(meta, payload);

            // assert
            then(matchingHandler).shouldHaveNoInteractions();
        }

        @DisplayName("매칭되는 handler가 없으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNoHandlerFound() {
            // arrange
            productMetricsService = new ProductMetricsService(
                    eventHandledRepository, List.of(nonMatchingHandler));

            given(eventHandledRepository.markIfAbsent("event-1")).willReturn(true);
            given(nonMatchingHandler.supports(MetricsEventType.LIKED)).willReturn(false);

            MetricsPayload.Like payload = new MetricsPayload.Like(1L, true);
            MetricsEventMeta meta = new MetricsEventMeta("event-1", MetricsEventType.LIKED);

            // act & assert
            assertThatThrownBy(() -> productMetricsService.handleEvent(meta, payload))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
