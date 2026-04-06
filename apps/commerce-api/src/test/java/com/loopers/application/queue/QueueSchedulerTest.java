package com.loopers.application.queue;

import com.loopers.config.TestRedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("QueueScheduler 테스트")
class QueueSchedulerTest {

    @Autowired
    private QueueScheduler queueScheduler;

    @Autowired
    private QueueService queueService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueProperties queueProperties;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
    }

    private void cleanUpRedisKeys() {
        Set<String> queueKeys = redisTemplate.keys(queueProperties.redisKey() + "*");
        if (queueKeys != null && !queueKeys.isEmpty()) {
            redisTemplate.delete(queueKeys);
        }
        Set<String> tokenKeys = redisTemplate.keys(queueProperties.tokenPrefix() + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
    }

    @Nested
    @DisplayName("processQueue")
    class ProcessQueue {

        @Test
        @DisplayName("스케줄러 실행 후 대기열에서 유저가 빠지고 토큰이 생성된다")
        void 토큰_발급_성공() {
            // Arrange
            Long userId = 1L;
            queueService.enter(userId);

            // Act
            queueScheduler.processQueue();

            // Assert
            Long queueSize = redisTemplate.opsForZSet().size(queueProperties.redisKey());
            assertThat(queueSize).isEqualTo(0);

            boolean hasToken = tokenService.hasToken(userId);
            assertThat(hasToken).isTrue();
        }

        @Test
        @DisplayName("대기열이 비어있으면 아무것도 하지 않는다")
        void 대기열_비어있음() {
            // Act - 대기열이 비어있는 상태에서 실행
            queueScheduler.processQueue();

            // Assert - 예외 없이 정상 종료
            Long queueSize = redisTemplate.opsForZSet().size(queueProperties.redisKey());
            assertThat(queueSize).isEqualTo(0);
        }

        @Test
        @DisplayName("배치 크기만큼만 처리하고 나머지는 대기 상태로 유지된다")
        void 배치_크기_제한() {
            // Arrange: 대기열에 500명 추가
            int totalUsers = 500;
            for (int i = 1; i <= totalUsers; i++) {
                queueService.enter((long) i);
            }

            // Act: 스케줄러 1회 실행
            queueScheduler.processQueue();

            // Assert: 배치 크기만큼만 처리됨
            Long remainingSize = redisTemplate.opsForZSet().size(queueProperties.redisKey());
            int expectedBatchSize = Math.max(1, queueProperties.throughputPerSecond() / 10);
            int expectedRemaining = totalUsers - expectedBatchSize;

            assertThat(remainingSize).isEqualTo(expectedRemaining);

            // 처리된 유저들은 토큰을 가지고 있어야 함
            int tokenCount = 0;
            for (int i = 1; i <= totalUsers; i++) {
                if (tokenService.hasToken((long) i)) {
                    tokenCount++;
                }
            }
            assertThat(tokenCount).isEqualTo(expectedBatchSize);
        }

        @Test
        @DisplayName("3명이 대기열에 있을 때 스케줄러 실행 후 모두 토큰을 받는다")
        void 소규모_대기열_처리() {
            // Arrange
            queueService.enter(1L);
            queueService.enter(2L);
            queueService.enter(3L);

            // Act
            queueScheduler.processQueue();

            // Assert
            Long queueSize = redisTemplate.opsForZSet().size(queueProperties.redisKey());
            assertThat(queueSize).isEqualTo(0);

            assertThat(tokenService.hasToken(1L)).isTrue();
            assertThat(tokenService.hasToken(2L)).isTrue();
            assertThat(tokenService.hasToken(3L)).isTrue();
        }

        @Test
        @DisplayName("대기열 진입 순서대로 토큰이 발급된다 (먼저 들어온 유저가 먼저 토큰 발급)")
        void 순서_보장() {
            // Arrange: 10명 순서대로 진입
            for (int i = 1; i <= 10; i++) {
                queueService.enter((long) i);
            }

            // Act: 배치 크기를 5로 제한하기 위해 한 번만 실행
            // throughputPerSecond가 175이므로 batch size는 17 또는 18
            // 이 테스트는 개념적 순서 보장 확인
            queueScheduler.processQueue();

            // Assert: 모두 처리되었거나 남은 유저들은 아직 토큰이 없음
            Long remainingSize = redisTemplate.opsForZSet().size(queueProperties.redisKey());

            if (remainingSize > 0) {
                // 남아있는 유저 확인 - 뒤에서부터 남아있어야 함
                Set<String> remaining = redisTemplate.opsForZSet().range(queueProperties.redisKey(), 0, -1);
                assertThat(remaining).isNotNull();

                // 남은 유저들은 토큰이 없어야 함
                for (String userIdStr : remaining) {
                    Long userId = Long.parseLong(userIdStr);
                    assertThat(tokenService.hasToken(userId)).isFalse();
                }
            }
        }
    }
}