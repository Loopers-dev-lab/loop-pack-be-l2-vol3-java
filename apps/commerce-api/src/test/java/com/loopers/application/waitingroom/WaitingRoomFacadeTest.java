package com.loopers.application.waitingroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.waitingroom.dto.WaitingRoomResult;
import com.loopers.domain.waitingroom.WaitingRoomPosition;
import com.loopers.domain.waitingroom.WaitingRoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("WaitingRoomFacade 단위 테스트")
@ExtendWith(MockitoExtension.class)
class WaitingRoomFacadeTest {

    @Mock
    WaitingRoomService waitingRoomService;

    @InjectMocks
    WaitingRoomFacade waitingRoomFacade;

    @DisplayName("대기열에 진입할 때, ")
    @Nested
    class Enter {

        @DisplayName("WaitingRoomResult로 변환하여 반환한다.")
        @Test
        void enter_returnsResult() {
            // arrange
            when(waitingRoomService.enter(1L)).thenReturn(WaitingRoomPosition.of(0, 1, 20));

            // act
            WaitingRoomResult result = waitingRoomFacade.enter(1L);

            // assert
            assertAll(
                    () -> assertThat(result.status()).isEqualTo("WAITING"),
                    () -> assertThat(result.position()).isEqualTo(1),
                    () -> assertThat(result.totalWaiting()).isEqualTo(1));
        }
    }

    @DisplayName("순번을 조회할 때, ")
    @Nested
    class GetPosition {

        @DisplayName("대기 중이면 WAITING 상태의 Result를 반환한다.")
        @Test
        void getPosition_waiting_returnsResult() {
            // arrange
            when(waitingRoomService.getPosition(1L)).thenReturn(WaitingRoomPosition.of(5, 100, 20));

            // act
            WaitingRoomResult result = waitingRoomFacade.getPosition(1L);

            // assert
            assertAll(
                    () -> assertThat(result.status()).isEqualTo("WAITING"),
                    () -> assertThat(result.position()).isEqualTo(6),
                    () -> assertThat(result.totalWaiting()).isEqualTo(100),
                    () -> assertThat(result.token()).isNull());
        }

        @DisplayName("토큰이 발급되면 ENTERED 상태의 Result를 반환한다.")
        @Test
        void getPosition_entered_returnsResult() {
            // arrange
            when(waitingRoomService.getPosition(1L)).thenReturn(WaitingRoomPosition.ready("abc-123"));

            // act
            WaitingRoomResult result = waitingRoomFacade.getPosition(1L);

            // assert
            assertAll(
                    () -> assertThat(result.status()).isEqualTo("ENTERED"),
                    () -> assertThat(result.token()).isEqualTo("abc-123"));
        }
    }

    @DisplayName("대기열을 취소할 때, ")
    @Nested
    class Cancel {

        @DisplayName("WaitingRoomService.cancel()을 호출한다.")
        @Test
        void cancel_delegatesToService() {
            // act
            waitingRoomFacade.cancel(1L);

            // assert
            verify(waitingRoomService).cancel(1L);
        }
    }
}
