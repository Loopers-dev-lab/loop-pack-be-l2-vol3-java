package com.loopers.application.queue;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.support.queue.WaitingQueueAdmitter;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueAdmissionScheduler 단위 테스트")
class QueueAdmissionSchedulerTest {

    @InjectMocks
    private QueueAdmissionScheduler scheduler;

    @Mock
    private WaitingQueueAdmitter waitingQueueAdmitter;

    @DisplayName("입장을 허용할 때,")
    @Nested
    class Admit {

        @DisplayName("대기열이 비어있으면, 이동되는 사용자가 없다.")
        @Test
        void noTransfer_whenQueueIsEmpty() {
            // arrange
            given(waitingQueueAdmitter.admit(18)).willReturn(Collections.emptyList());

            // act
            scheduler.admit();

            // assert
            then(waitingQueueAdmitter).should().admit(18);
        }

        @DisplayName("대기자가 있으면, 원자적으로 입장열로 이동한다.")
        @Test
        void transfersAtomically_whenUsersExist() {
            // arrange
            given(waitingQueueAdmitter.admit(18)).willReturn(List.of(1L, 2L, 3L));

            // act
            scheduler.admit();

            // assert
            then(waitingQueueAdmitter).should().admit(18);
        }
    }
}
