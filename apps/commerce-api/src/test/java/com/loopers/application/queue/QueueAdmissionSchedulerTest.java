package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueAdmissionScheduler 단위 테스트")
class QueueAdmissionSchedulerTest {

    @Mock
    private QueueService queueService;

    @InjectMocks
    private QueueAdmissionScheduler scheduler;

    @Test
    @DisplayName("대기열에서 배치 크기만큼 꺼내 토큰을 발급한다")
    void processQueue_success() {
        // Given
        given(queueService.processBatch(7)).willReturn(List.of(1L, 2L, 3L));

        // When
        scheduler.processQueue();

        // Then
        then(queueService).should().processBatch(7);
    }

    @Test
    @DisplayName("대기열이 비어있으면 아무것도 하지 않는다")
    void processQueue_empty() {
        // Given
        given(queueService.processBatch(7)).willReturn(Collections.emptyList());

        // When
        scheduler.processQueue();

        // Then
        then(queueService).should().processBatch(7);
    }
}
