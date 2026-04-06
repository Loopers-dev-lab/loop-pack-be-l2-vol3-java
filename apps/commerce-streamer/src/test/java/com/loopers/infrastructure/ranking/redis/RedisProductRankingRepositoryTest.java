package com.loopers.infrastructure.ranking.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@ExtendWith(MockitoExtension.class)
class RedisProductRankingRepositoryTest {

    @InjectMocks
    private RedisProductRankingRepository repository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @DisplayName("incrementScores를 호출할 때,")
    @Nested
    class IncrementScores {

        @DisplayName("Redis Pipeline으로 ZINCRBY와 EXPIRE를 실행한다.")
        @Test
        void executesPipelineWithZincrbyAndExpire() {
            // arrange
            String key = "ranking:v1:all:20260406";
            Map<Long, Double> productScores = Map.of(1L, 0.1, 2L, 0.2);
            given(redisTemplate.getStringSerializer()).willReturn(new StringRedisSerializer());
            given(redisTemplate.executePipelined(any(RedisCallback.class))).willReturn(List.of());

            // act
            repository.incrementScores(key, productScores);

            // assert
            then(redisTemplate).should().executePipelined(any(RedisCallback.class));
        }

        @DisplayName("빈 Map이면, Pipeline을 실행하지 않는다.")
        @Test
        void skips_whenEmptyScores() {
            // act
            repository.incrementScores("ranking:v1:all:20260406", Map.of());

            // assert
            then(redisTemplate).should(times(0)).executePipelined(any(RedisCallback.class));
        }
    }
}
