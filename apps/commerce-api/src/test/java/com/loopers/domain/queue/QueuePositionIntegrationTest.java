package com.loopers.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QueuePositionIntegrationTest {

    @Autowired
    private QueueFacade queueFacade;

    @Autowired
    private QueueService queueService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("순번 조회 시, ")
    @Nested
    class GetPosition {

        @DisplayName("예상 대기 시간이 계산되어 반환된다.")
        @Test
        void returnsEstimatedWaitTime() {
            // arrange — 10명 진입, 내가 10번째
            for (int i = 1; i <= 10; i++) {
                queueService.enter("user-" + i);
            }

            // act
            QueuePositionInfo info = queueFacade.getPosition("user-10");

            // assert — estimatedWaitSeconds > 0
            assertThat(info.estimatedWaitSeconds()).isPositive();
        }

        @DisplayName("토큰이 발급된 유저 조회 시 token이 포함된다.")
        @Test
        void includesToken_whenUserHasToken() {
            // arrange
            String userId = "user-1";
            queueService.enter(userId);
            String token = tokenService.issue(userId);

            // act
            QueuePositionInfo info = queueFacade.getPosition(userId);

            // assert
            assertThat(info.token()).isEqualTo(token);
        }

        @DisplayName("토큰이 없는 유저 조회 시 token은 null이다.")
        @Test
        void tokenIsNull_whenUserHasNoToken() {
            // arrange
            queueService.enter("user-1");

            // act
            QueuePositionInfo info = queueFacade.getPosition("user-1");

            // assert
            assertThat(info.token()).isNull();
        }

        @DisplayName("순번이 클수록 nextPollAfter가 길다.")
        @Test
        void longerNextPollAfter_forHigherPosition() {
            // arrange
            for (int i = 1; i <= 100; i++) {
                queueService.enter("user-" + i);
            }

            // act
            QueuePositionInfo nearInfo = queueFacade.getPosition("user-1");   // 1번
            QueuePositionInfo farInfo = queueFacade.getPosition("user-100");  // 100번

            // assert
            assertThat(farInfo.nextPollAfterSeconds()).isGreaterThanOrEqualTo(nearInfo.nextPollAfterSeconds());
        }
    }
}