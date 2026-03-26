package com.loopers.domain.eventhandled;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventHandledService 단위 테스트")
class EventHandledServiceTest {

    @Mock
    private EventHandledRepository eventHandledRepository;

    @InjectMocks
    private EventHandledService eventHandledService;

    @Nested
    @DisplayName("isAlreadyHandled - 중복 처리 확인")
    class IsAlreadyHandled {

        @Test
        @DisplayName("성공: 처리된 적 없으면 false를 반환한다")
        void isAlreadyHandled_returnsFalse() {
            // Given
            String eventId = "test-event-id";
            given(eventHandledRepository.existsByEventId(eventId)).willReturn(false);

            // When
            boolean result = eventHandledService.isAlreadyHandled(eventId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("성공: 이미 처리되었으면 true를 반환한다")
        void isAlreadyHandled_returnsTrue() {
            // Given
            String eventId = "test-event-id";
            given(eventHandledRepository.existsByEventId(eventId)).willReturn(true);

            // When
            boolean result = eventHandledService.isAlreadyHandled(eventId);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("markAsHandled - 처리 완료 기록")
    class MarkAsHandled {

        @Test
        @DisplayName("성공: EventHandled를 저장한다")
        void markAsHandled_savesEvent() {
            // Given
            String eventId = "test-event-id";

            // When
            eventHandledService.markAsHandled(eventId);

            // Then
            then(eventHandledRepository).should().save(any(EventHandled.class));
        }
    }
}
