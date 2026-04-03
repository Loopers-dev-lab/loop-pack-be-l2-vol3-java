package com.loopers.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
class QueueServiceIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 진입 시, ")
    @Nested
    class Enter {

        @DisplayName("userId로 진입하면 순번이 부여된다.")
        @Test
        void assignsPosition_whenUserEntersQueue() {
            // arrange
            String userId = "user-1";

            // act
            long position = queueService.enter(userId);

            // assert
            assertThat(position).isEqualTo(1L);
        }

        @DisplayName("진입하면 presence 키가 생성된다.")
        @Test
        void createsPresenceKey_whenUserEntersQueue() {
            // arrange
            String userId = "user-1";

            // act
            queueService.enter(userId);

            // assert
            assertThat(redisTemplate.hasKey("presence:" + userId)).isTrue();
        }

        @DisplayName("같은 userId로 두 번 진입해도 첫 번째 순번이 유지된다.")
        @Test
        void keepFirstPosition_whenSameUserEntersTwice() {
            // arrange
            String userId = "user-1";
            queueService.enter(userId);

            // act
            long position = queueService.enter(userId);

            // assert
            assertThat(position).isEqualTo(1L);
        }
    }

    @DisplayName("순번 조회 시, ")
    @Nested
    class GetPosition {

        @DisplayName("대기열에 있는 userId의 순번을 1-indexed로 반환한다.")
        @Test
        void returnsPosition_whenUserIsInQueue() {
            // arrange
            queueService.enter("user-1");
            queueService.enter("user-2");
            queueService.enter("user-3");

            // act
            long position = queueService.getPosition("user-3");

            // assert
            assertThat(position).isEqualTo(3L);
        }

        @DisplayName("대기열에 없는 userId 조회 시 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserIsNotInQueue() {
            // arrange
            String userId = "user-not-exists";

            // act & assert
            CoreException exception = assertThrows(CoreException.class,
                () -> queueService.getPosition(userId));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("전체 대기 인원 조회 시, ")
    @Nested
    class GetTotalCount {

        @DisplayName("현재 대기 중인 전체 인원 수를 반환한다.")
        @Test
        void returnsTotalCount_whenUsersAreInQueue() {
            // arrange
            queueService.enter("user-1");
            queueService.enter("user-2");
            queueService.enter("user-3");

            // act
            long totalCount = queueService.getTotalCount();

            // assert
            assertThat(totalCount).isEqualTo(3L);
        }
    }
}