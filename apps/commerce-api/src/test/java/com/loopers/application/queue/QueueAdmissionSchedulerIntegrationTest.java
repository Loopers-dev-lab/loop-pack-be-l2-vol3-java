package com.loopers.application.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import com.loopers.config.redis.RedisConfig;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.queue.WaitingQueue;

@DisplayName("QueueAdmissionScheduler 통합 테스트")
@TestPropertySource(properties = "queue.enabled=true")
class QueueAdmissionSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private QueueAdmissionScheduler scheduler;

    @Autowired
    private WaitingQueue waitingQueue;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @DisplayName("스케줄러가 실행될 때,")
    @Nested
    class Admit {

        @DisplayName("대기열에 3명이 있으면, 2명만 입장 토큰을 발급받는다.")
        @Test
        void admitsBatchSize_whenMoreUsersWaiting() {
            // arrange
            waitingQueue.enter(1L);
            waitingQueue.enter(2L);
            waitingQueue.enter(3L);

            // act
            scheduler.admit();

            // assert
            assertAll(
                    () -> assertThat(waitingQueue.getTotalCount()).isEqualTo(1),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:1")).isNotNull(),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:2")).isNotNull(),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:3")).isNull()
            );
        }

        @DisplayName("대기열에 1명만 있으면, 1명만 입장 토큰을 발급받는다.")
        @Test
        void admitsAvailable_whenFewerThanBatchSize() {
            // arrange
            waitingQueue.enter(1L);

            // act
            scheduler.admit();

            // assert
            assertAll(
                    () -> assertThat(waitingQueue.getTotalCount()).isZero(),
                    () -> assertThat(redisTemplate.opsForValue().get("entry-token:1")).isNotNull()
            );
        }

        @DisplayName("대기열이 비어있으면, 예외 없이 정상 종료된다.")
        @Test
        void completesNormally_whenQueueIsEmpty() {
            // act
            scheduler.admit();

            // assert
            assertThat(waitingQueue.getTotalCount()).isZero();
        }
    }
}
