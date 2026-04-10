package com.loopers.batch;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxRelayMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventRelay 단위 테스트")
class OutboxEventRelayTest {

    @Mock
    OutboxEventRepository outboxRepository;

    @Mock
    OutboxEventProcessor outboxEventProcessor;

    @Mock
    OutboxRelayMetrics outboxRelayMetrics;

    @InjectMocks
    OutboxEventRelay outboxEventRelay;

    @Test
    @DisplayName("PENDING 이벤트가 있으면 processor에 위임한다")
    void relay_WithPendingEvents_ShouldDelegateToProcessor() {
        OutboxEventModel event = OutboxEventModel.create(
                "ORDER", "1", "ORDER_CREATED",
                "order-events", "order-1",
                "{\"orderId\":1}"
        );
        Object timerToken = new Object();
        when(outboxRelayMetrics.startRelayTimer()).thenReturn(timerToken);
        when(outboxRepository.findPendingEvents(100)).thenReturn(List.of(event));
        when(outboxEventProcessor.publishAndMark(event)).thenReturn(true);

        outboxEventRelay.relay();

        verify(outboxEventProcessor).publishAndMark(event);
        verify(outboxRelayMetrics).recordPublishSuccess();
    }

    @Test
    @DisplayName("PENDING 이벤트가 없으면 processor를 호출하지 않는다")
    void relay_WithEmptyList_ShouldNotCallProcessor() {
        Object timerToken = new Object();
        when(outboxRelayMetrics.startRelayTimer()).thenReturn(timerToken);
        when(outboxRepository.findPendingEvents(100)).thenReturn(Collections.emptyList());

        outboxEventRelay.relay();

        verify(outboxEventProcessor, never()).publishAndMark(any());
    }

    @Test
    @DisplayName("폴링 쿼리 실패 시 다음 호출은 백오프로 스킵된다")
    void relay_WhenQueryFails_ShouldApplyBackoff() {
        Object timerToken = new Object();
        when(outboxRelayMetrics.startRelayTimer()).thenReturn(timerToken);
        when(outboxRepository.findPendingEvents(100))
                .thenThrow(new RuntimeException("DB connection failed"));

        // 첫 번째 호출: 에러 발생 → 백오프 설정
        outboxEventRelay.relay();

        // 두 번째 호출: 백오프 기간이므로 폴링 쿼리 호출하지 않음
        outboxEventRelay.relay();

        verify(outboxRepository, times(1)).findPendingEvents(100);
    }
}
