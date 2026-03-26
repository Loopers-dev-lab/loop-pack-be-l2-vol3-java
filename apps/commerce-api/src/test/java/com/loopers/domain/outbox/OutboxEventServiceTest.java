package com.loopers.domain.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventService 단위 테스트")
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventService outboxEventService;

    @Nested
    @DisplayName("save - Outbox 이벤트 저장")
    class Save {

        @Test
        @DisplayName("성공: OutboxEvent를 저장한다")
        void save_delegatesToRepository() {
            // Given
            OutboxEvent event = OutboxEvent.create("catalog-events", "PRODUCT_LIKED", "123", "{}");
            given(outboxEventRepository.save(event)).willReturn(event);

            // When
            OutboxEvent result = outboxEventService.save(event);

            // Then
            assertThat(result).isEqualTo(event);
            then(outboxEventRepository).should().save(event);
        }
    }

    @Nested
    @DisplayName("findPendingEvents - INIT 상태 이벤트 조회")
    class FindPendingEvents {

        @Test
        @DisplayName("성공: INIT 상태의 이벤트를 limit 개수만큼 조회한다")
        void findPendingEvents_returnsInitEvents() {
            // Given
            int limit = 10;
            OutboxEvent event = OutboxEvent.create("catalog-events", "PRODUCT_LIKED", "123", "{}");
            given(outboxEventRepository.findAllByStatusOrderByCreatedAtAsc(OutboxStatus.INIT, limit))
                    .willReturn(List.of(event));

            // When
            List<OutboxEvent> result = outboxEventService.findPendingEvents(limit);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(event);
        }
    }

    @Nested
    @DisplayName("markAsSent - 발송 완료 처리")
    class MarkAsSent {

        @Test
        @DisplayName("성공: OutboxEvent의 상태를 SENT로 변경한다")
        void markAsSent_changesStatusToSent() {
            // Given
            OutboxEvent event = OutboxEvent.create("catalog-events", "PRODUCT_LIKED", "123", "{}");

            // When
            outboxEventService.markAsSent(event);

            // Then
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        }
    }
}
