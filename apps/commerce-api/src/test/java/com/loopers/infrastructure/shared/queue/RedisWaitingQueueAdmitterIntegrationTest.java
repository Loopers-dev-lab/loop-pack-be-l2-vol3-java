package com.loopers.infrastructure.shared.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.ConcurrentTestHelper;
import com.loopers.support.queue.WaitingQueueAdmitter;
import com.loopers.support.queue.WaitingQueue;

@DisplayName("RedisWaitingQueueAdmitter 통합 테스트")
class RedisWaitingQueueAdmitterIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WaitingQueueAdmitter waitingQueueAdmitter;

    @Autowired
    private WaitingQueue waitingQueue;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @DisplayName("대기열에서 토큰을 발급할 때,")
    @Nested
    class Transfer {

        @DisplayName("대기열이 비어있으면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenQueueIsEmpty() {
            // act
            List<Long> result = waitingQueueAdmitter.admit(5);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("대기열에서 제거되고 entry-token이 발급된다.")
        @Test
        void removesFromQueueAndIssuesToken() {
            // arrange
            waitingQueue.enter(1L);
            waitingQueue.enter(2L);
            waitingQueue.enter(3L);

            // act
            List<Long> result = waitingQueueAdmitter.admit(2);

            // assert
            assertAll(
                    () -> assertThat(result).containsExactly(1L, 2L),
                    () -> assertThat(waitingQueue.getTotalCount()).isEqualTo(1),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:1")).isNotNull(),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:2")).isNotNull(),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:3")).isNull()
            );
        }

        @DisplayName("요청 수보다 대기 인원이 적으면, 있는 만큼만 이동한다.")
        @Test
        void movesAvailableUsers_whenFewerThanRequested() {
            // arrange
            waitingQueue.enter(1L);

            // act
            List<Long> result = waitingQueueAdmitter.admit(5);

            // assert
            assertAll(
                    () -> assertThat(result).containsExactly(1L),
                    () -> assertThat(waitingQueue.getTotalCount()).isZero(),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:1")).isNotNull()
            );
        }

        @DisplayName("발급된 토큰은 UUID 형식이다.")
        @Test
        void issuedTokenIsUuidFormat() {
            // arrange
            waitingQueue.enter(1L);

            // act
            waitingQueueAdmitter.admit(1);

            // assert
            String token = redisTemplate.opsForValue().get("entry-token:1");
            assertThat(token).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
            );
        }
    }

    @DisplayName("배치 크기를 초과하는 동시 입장 처리 요청이 들어올 때,")
    @Nested
    class ConcurrentAdmit {

        @DisplayName("100명 대기 중 10개 스레드가 동시에 admit(18)을 호출하면, 예외 없이 전원 입장 처리된다.")
        @Test
        void allAdmitted_whenConcurrentAdmitExceedsBatchSize() throws InterruptedException {
            // arrange
            int totalUsers = 100;
            for (long userId = 1; userId <= totalUsers; userId++) {
                waitingQueue.enter(userId);
            }

            // act
            ConcurrentTestHelper.ConcurrentResult result = ConcurrentTestHelper.executeConcurrently(
                    10, () -> waitingQueueAdmitter.admit(18)
            );

            // assert
            Set<String> tokenKeys = redisTemplate.keys("entry-token:*");
            assertAll(
                    () -> assertThat(result.successCount()).isEqualTo(10),
                    () -> assertThat(result.failCount()).isZero(),
                    () -> assertThat(waitingQueue.getTotalCount()).isZero(),
                    () -> assertThat(tokenKeys).hasSize(totalUsers)
            );
        }
    }
}
