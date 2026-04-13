package com.loopers.infrastructure.eventhandled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisEventHandledRepositoryTest {

    @InjectMocks
    private RedisEventHandledRepository redisEventHandledRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @DisplayName("이벤트 중복 여부를 확인할 때,")
    @Nested
    class MarkIfAbsent {

        @DisplayName("새 이벤트이면, true를 반환한다.")
        @Test
        void returnsTrue_whenNewEvent() {
            // arrange
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(eq("event-handled:event-1"), eq("1"), eq(Duration.ofDays(14))))
                    .willReturn(true);

            // act
            boolean result = redisEventHandledRepository.markIfAbsent("event-1");

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("이미 처리된 이벤트이면, false를 반환한다.")
        @Test
        void returnsFalse_whenDuplicateEvent() {
            // arrange
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.setIfAbsent(eq("event-handled:event-1"), eq("1"), eq(Duration.ofDays(14))))
                    .willReturn(false);

            // act
            boolean result = redisEventHandledRepository.markIfAbsent("event-1");

            // assert
            assertThat(result).isFalse();
        }

        @DisplayName("Redis 장애 시, true를 반환하여 처리를 계속한다.")
        @Test
        void returnsTrue_whenRedisFailure() {
            // arrange
            given(redisTemplate.opsForValue()).willThrow(new RuntimeException("Redis connection refused"));

            // act
            boolean result = redisEventHandledRepository.markIfAbsent("event-1");

            // assert
            assertThat(result).isTrue();
        }
    }
}
