package com.loopers.support.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheManagerTest {

    private RedisCacheManager redisCacheManager;

    @Mock
    private RedisTemplate<String, String> readTemplate;

    @Mock
    private RedisTemplate<String, String> writeTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> readValueOps;

    @Mock
    private ValueOperations<String, String> writeValueOps;

    @BeforeEach
    void setUp() {
        redisCacheManager = new RedisCacheManager(readTemplate, writeTemplate, objectMapper);
    }

    @DisplayName("GET 연산")
    @Nested
    class Get {

        @DisplayName("캐시에 데이터가 있으면 역직렬화하여 반환한다")
        @Test
        void returnsDeserialized_whenCacheHit() throws Exception {
            // arrange
            String json = "{\"id\":1}";
            TestDto expected = new TestDto(1L, "test");

            when(readTemplate.opsForValue()).thenReturn(readValueOps);
            when(readValueOps.get("key:1")).thenReturn(json);
            when(objectMapper.readValue(json, TestDto.class)).thenReturn(expected);

            // act
            Optional<TestDto> result = redisCacheManager.get("key:1", TestDto.class);

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(1L);
        }

        @DisplayName("캐시에 데이터가 없으면 빈 Optional을 반환한다")
        @Test
        void returnsEmpty_whenCacheMiss() {
            // arrange
            when(readTemplate.opsForValue()).thenReturn(readValueOps);
            when(readValueOps.get("key:1")).thenReturn(null);

            // act
            Optional<TestDto> result = redisCacheManager.get("key:1", TestDto.class);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("Redis 장애 시 빈 Optional을 반환한다")
        @Test
        void returnsEmpty_whenRedisError() {
            // arrange
            when(readTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 연결 실패"));

            // act
            Optional<TestDto> result = redisCacheManager.get("key:1", TestDto.class);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("PUT 연산")
    @Nested
    class Put {

        @DisplayName("JSON으로 직렬화하여 TTL과 함께 저장한다")
        @Test
        void savesWithTtl() throws Exception {
            // arrange
            TestDto dto = new TestDto(1L, "test");
            String json = "{\"id\":1}";

            when(objectMapper.writeValueAsString(dto)).thenReturn(json);
            when(writeTemplate.opsForValue()).thenReturn(writeValueOps);

            // act
            redisCacheManager.put("key:1", dto, 3600);

            // assert
            verify(writeValueOps).set("key:1", json, 3600, TimeUnit.SECONDS);
        }

        @DisplayName("Redis 장애 시 예외를 던지지 않는다")
        @Test
        void doesNotThrow_whenRedisError() throws Exception {
            // arrange
            TestDto dto = new TestDto(1L, "test");
            when(objectMapper.writeValueAsString(dto)).thenReturn("{\"id\":1}");
            when(writeTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 연결 실패"));

            // act & assert (no exception)
            redisCacheManager.put("key:1", dto, 3600);
        }
    }

    @DisplayName("EVICT 연산")
    @Nested
    class Evict {

        @DisplayName("키를 삭제한다")
        @Test
        void deletesKey() {
            // act
            redisCacheManager.evict("key:1");

            // assert
            verify(writeTemplate).delete("key:1");
        }

        @DisplayName("Redis 장애 시 예외를 던지지 않는다")
        @Test
        void doesNotThrow_whenRedisError() {
            // arrange
            when(writeTemplate.delete("key:1")).thenThrow(new RuntimeException("Redis 연결 실패"));

            // act & assert (no exception)
            redisCacheManager.evict("key:1");
        }
    }

    record TestDto(Long id, String name) {}
}
