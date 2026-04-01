package com.loopers.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TokenSchedulerIntegrationTest {

    @Autowired
    private TokenScheduler tokenScheduler;

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

    @DisplayName("스케줄러 실행 시, ")
    @Nested
    class Issue {

        @DisplayName("대기열 상위 N명에게 토큰이 발급된다.")
        @Test
        void issuesTokens_forTopNUsersInQueue() {
            // arrange
            queueService.enter("user-1");
            queueService.enter("user-2");
            queueService.enter("user-3");

            // act
            tokenScheduler.issueTokens();

            // assert
            assertThat(tokenService.findToken("user-1")).isPresent();
            assertThat(tokenService.findToken("user-2")).isPresent();
            assertThat(tokenService.findToken("user-3")).isPresent();
        }

        @DisplayName("토큰 발급된 userId는 대기열에서 제거된다.")
        @Test
        void removesUsersFromQueue_afterTokenIssuance() {
            // arrange
            queueService.enter("user-1");
            queueService.enter("user-2");

            // act
            tokenScheduler.issueTokens();

            // assert
            assertThat(queueService.getTotalCount()).isZero();
        }

        @DisplayName("대기열 인원이 N보다 적으면 있는 만큼만 발급된다.")
        @Test
        void issuesTokens_onlyForAvailableUsers_whenFewerThanN() {
            // arrange
            queueService.enter("user-1");

            // act
            tokenScheduler.issueTokens();

            // assert
            assertThat(tokenService.findToken("user-1")).isPresent();
            assertThat(queueService.getTotalCount()).isZero();
        }

        @DisplayName("대기열이 비어있으면 예외 없이 정상 종료된다.")
        @Test
        void completesNormally_whenQueueIsEmpty() {
            // arrange (빈 대기열)

            // act & assert
            assertDoesNotThrow(() -> tokenScheduler.issueTokens());
        }

        @DisplayName("이미 토큰이 있는 userId는 중복 발급되지 않는다.")
        @Test
        void doesNotIssueToken_whenUserAlreadyHasToken() {
            // arrange
            queueService.enter("user-1");
            tokenScheduler.issueTokens(); // 첫 번째 발급
            String firstToken = tokenService.findToken("user-1").orElseThrow();

            queueService.enter("user-1"); // 재진입

            // act
            tokenScheduler.issueTokens(); // 두 번째 실행

            // assert — 토큰 값이 변경되지 않음
            String secondToken = tokenService.findToken("user-1").orElseThrow();
            assertThat(secondToken).isEqualTo(firstToken);
        }
    }
}