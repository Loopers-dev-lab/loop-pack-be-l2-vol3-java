package com.loopers.infrastructure.shared.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.domain.shared.cache.CacheRepository;
import com.loopers.domain.shared.cache.CacheType;
import com.loopers.support.page.Page;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class RedisCacheRepositoryIntegrationTest {

    private static final CacheType<String> STRING_TYPE = new CacheType<>() {};
    private static final CacheType<Page<TestItem>> PAGE_TYPE = new CacheType<>() {};

    @Autowired
    private CacheRepository cacheRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("캐시에 값을 저장하고 조회할 때,")
    @Nested
    class PutAndGet {

        @DisplayName("단순 타입을 저장하면, 동일한 값이 조회된다.")
        @Test
        void returnsStoredValue_whenSimpleTypePut() {
            // arrange
            String key = "test:simple";
            String value = "hello";

            // act
            cacheRepository.put(key, value);
            String result = cacheRepository.get(key, STRING_TYPE);

            // assert
            assertThat(result).isEqualTo("hello");
        }

        @DisplayName("파라미터화된 타입을 저장하면, 타입 정보가 보존되어 조회된다.")
        @Test
        void returnsStoredValue_whenParameterizedTypePut() {
            // arrange
            String key = "test:page";
            Page<TestItem> value = new Page<>(
                    List.of(new TestItem(1L, "item1"), new TestItem(2L, "item2")),
                    true
            );

            // act
            cacheRepository.put(key, value);
            Page<TestItem> result = cacheRepository.get(key, PAGE_TYPE);

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.content().get(0).name()).isEqualTo("item1"),
                    () -> assertThat(result.hasNext()).isTrue()
            );
        }

        @DisplayName("존재하지 않는 키를 조회하면, null이 반환된다.")
        @Test
        void returnsNull_whenKeyDoesNotExist() {
            // act
            String result = cacheRepository.get("test:nonexistent", STRING_TYPE);

            // assert
            assertThat(result).isNull();
        }
    }

    @DisplayName("TTL을 지정하여 저장할 때,")
    @Nested
    class PutWithTtl {

        @DisplayName("TTL이 만료되면, null이 반환된다.")
        @Test
        void returnsNull_whenTtlExpired() throws InterruptedException {
            // arrange
            String key = "test:ttl";
            cacheRepository.put(key, "expiring", Duration.ofSeconds(1));

            // act
            Thread.sleep(1500);
            String result = cacheRepository.get(key, STRING_TYPE);

            // assert
            assertThat(result).isNull();
        }

        @DisplayName("TTL이 만료되기 전이면, 값이 조회된다.")
        @Test
        void returnsValue_whenTtlNotExpired() {
            // arrange
            String key = "test:ttl-alive";
            cacheRepository.put(key, "still-alive", Duration.ofMinutes(1));

            // act
            String result = cacheRepository.get(key, STRING_TYPE);

            // assert
            assertThat(result).isEqualTo("still-alive");
        }
    }

    @DisplayName("캐시를 삭제할 때,")
    @Nested
    class Evict {

        @DisplayName("패턴에 매칭되는 키가 모두 삭제된다.")
        @Test
        void deletesAllMatchingKeys_whenPatternProvided() {
            // arrange
            cacheRepository.put("product:list:1", "a");
            cacheRepository.put("product:list:2", "b");
            cacheRepository.put("order:list:1", "c");

            // act
            cacheRepository.evict("product:list:*");

            // assert
            assertAll(
                    () -> assertThat(cacheRepository.get("product:list:1", STRING_TYPE)).isNull(),
                    () -> assertThat(cacheRepository.get("product:list:2", STRING_TYPE)).isNull(),
                    () -> assertThat(cacheRepository.get("order:list:1", STRING_TYPE)).isEqualTo("c")
            );
        }
    }

    record TestItem(Long id, String name) {}
}
