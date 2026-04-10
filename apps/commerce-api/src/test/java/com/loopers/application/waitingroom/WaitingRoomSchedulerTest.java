package com.loopers.application.waitingroom;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.domain.waitingroom.WaitingRoomService;
import com.loopers.infrastructure.waitingroom.metrics.WaitingRoomProcessQueueMetrics;
import com.loopers.infrastructure.waitingroom.metrics.WaitingRoomSchedulerTimer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("WaitingRoomScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class WaitingRoomSchedulerTest {

    @Mock
    WaitingRoomService waitingRoomService;

    @Mock
    WaitingRoomSchedulerTimer schedulerTimer;

    @Mock
    WaitingRoomProcessQueueMetrics processQueueMetrics;

    @InjectMocks
    WaitingRoomScheduler waitingRoomScheduler;

    @DisplayName("스케줄러 실행 시 Timer를 통해 processQueue()를 호출하고 메트릭을 기록한다.")
    @Test
    void run_callsProcessQueueAndRecordsMetrics() {
        // arrange — Timer.record()가 Runnable을 실행하도록 설정
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(schedulerTimer).record(any(Runnable.class));
        when(waitingRoomService.processQueue()).thenReturn(List.of());

        // act
        waitingRoomScheduler.run();

        // assert
        verify(schedulerTimer).record(any(Runnable.class));
        verify(waitingRoomService).processQueue();
        verify(processQueueMetrics).recordBatch(List.of());
    }
}
