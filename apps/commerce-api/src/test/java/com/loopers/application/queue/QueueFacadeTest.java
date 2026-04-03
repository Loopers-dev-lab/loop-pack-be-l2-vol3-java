package com.loopers.application.queue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QueueFacadeTest {

    @InjectMocks
    private QueueFacade queueFacade;

    @Mock
    private QueueService queueService;

    @Mock
    private QueueTokenService queueTokenService;

    @DisplayName("대기열 위치 조회")
    @Nested
    class GetPosition {

        @DisplayName("토큰이 발급된 유저의 position 조회 시 토큰 정보가 포함된다")
        @Test
        void returnsTokenInfoWhenTokenExists() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            QueueService.QueueStatus status = new QueueService.QueueStatus(0, 10, 0);
            given(queueService.getPosition(eventId, userId)).willReturn(status);
            given(queueTokenService.getTokenInfo(eventId, userId))
                    .willReturn(Optional.of(new QueueTokenService.TokenInfo("tok_abc", 280L)));

            // when
            QueueInfo result = queueFacade.getPosition(eventId, userId);

            // then
            assertThat(result.position()).isEqualTo(0);
            assertThat(result.totalWaiting()).isEqualTo(10);
            assertThat(result.token()).isEqualTo("tok_abc");
            assertThat(result.tokenExpiresIn()).isEqualTo(280L);
        }

        @DisplayName("토큰이 없는 유저의 position 조회 시 token은 null이다")
        @Test
        void returnsNullTokenWhenNoToken() {
            // given
            String eventId = "event-1";
            Long userId = 2L;
            QueueService.QueueStatus status = new QueueService.QueueStatus(5, 10, 6);
            given(queueService.getPosition(eventId, userId)).willReturn(status);
            given(queueTokenService.getTokenInfo(eventId, userId)).willReturn(Optional.empty());

            // when
            QueueInfo result = queueFacade.getPosition(eventId, userId);

            // then
            assertThat(result.position()).isEqualTo(5);
            assertThat(result.totalWaiting()).isEqualTo(10);
            assertThat(result.estimatedWaitSeconds()).isEqualTo(6);
            assertThat(result.token()).isNull();
            assertThat(result.tokenExpiresIn()).isNull();
        }
    }
}
