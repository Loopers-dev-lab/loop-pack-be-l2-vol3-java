package com.loopers.infrastructure.shared.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.BaseIntegrationTest;
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

}
