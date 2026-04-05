package com.loopers.application.queue;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.support.queue.QueueProperties;
import com.loopers.support.queue.WaitingQueueAdmitter;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueueAdmissionScheduler 단위 테스트")
class QueueAdmissionSchedulerTest {

    @Mock
    private WaitingQueueAdmitter waitingQueueAdmitter;

    private final QueueProperties queueProperties = new QueueProperties(true, 2, 400);

    private QueueAdmissionScheduler scheduler() {
        return new QueueAdmissionScheduler(waitingQueueAdmitter, queueProperties);
    }

    @DisplayName("입장을 허용할 때,")
    @Nested
    class Admit {

        @DisplayName("대기열이 비어있으면, 이동되는 사용자가 없다.")
        @Test
        void noTransfer_whenQueueIsEmpty() {
            // arrange
            given(waitingQueueAdmitter.admit(2)).willReturn(Collections.emptyList());

            // act
            scheduler().admit();

            // assert
            then(waitingQueueAdmitter).should().admit(2);
        }

        @DisplayName("대기자가 있으면, 입장열로 이동한다.")
        @Test
        void transfers_whenUsersExist() {
            // arrange
            given(waitingQueueAdmitter.admit(2)).willReturn(List.of(1L, 2L));

            // act
            scheduler().admit();

            // assert
            then(waitingQueueAdmitter).should().admit(2);
        }
    }
}
