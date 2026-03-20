package com.loopers.infrastructure.cache;

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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("LikeCountCacheService 테스트")
class LikeCountCacheServiceTest {

    @Autowired
    private LikeCountCacheService likeCountCacheService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
    }

    private void cleanUpRedisKeys() {
        // 샤딩된 키 패턴 정리: {product:*}:like:*
        var keys = redisTemplate.keys("{product:*}:like:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Nested
    @DisplayName("increment 테스트")
    class Increment {

        @Test
        @DisplayName("성공 - 좋아요 수 증가")
        void 좋아요_수_증가() {
            // Arrange
            Long productId = 1L;

            // Act
            Long result = likeCountCacheService.increment(productId);

            // Assert
            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("성공 - 여러 번 증가")
        void 여러번_증가() {
            // Arrange
            Long productId = 1L;

            // Act
            likeCountCacheService.increment(productId);
            likeCountCacheService.increment(productId);
            Long result = likeCountCacheService.increment(productId);

            // Assert
            assertThat(result).isEqualTo(3L);
        }

        @Test
        @DisplayName("성공 - 증가 시 dirty 플래그 설정됨")
        void 증가시_dirty_플래그_설정() {
            // Arrange
            Long productId = 1L;

            // Act
            likeCountCacheService.increment(productId);

            // Assert
            Set<Long> dirtyIds = likeCountCacheService.getDirtyProductIds();
            assertThat(dirtyIds).contains(productId);
        }
    }

    @Nested
    @DisplayName("decrement 테스트")
    class Decrement {

        @Test
        @DisplayName("성공 - 좋아요 수 감소")
        void 좋아요_수_감소() {
            // Arrange
            Long productId = 1L;
            likeCountCacheService.increment(productId);
            likeCountCacheService.increment(productId);

            // Act
            Long result = likeCountCacheService.decrement(productId);

            // Assert
            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("성공 - 0 미만으로 내려가지 않음 (음수는 0으로 처리)")
        void 음수_방지() {
            // Arrange
            Long productId = 1L;

            // Act
            Long result = likeCountCacheService.decrement(productId);

            // Assert
            assertThat(result).isEqualTo(0L);

            // 캐시에서도 0으로 저장되어 있는지 확인
            Optional<Long> cached = likeCountCacheService.get(productId);
            assertThat(cached).isPresent();
            assertThat(cached.get()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("get 테스트")
    class Get {

        @Test
        @DisplayName("성공 - 존재하는 카운트 조회")
        void 존재하는_카운트_조회() {
            // Arrange
            Long productId = 1L;
            likeCountCacheService.increment(productId);
            likeCountCacheService.increment(productId);

            // Act
            Optional<Long> result = likeCountCacheService.get(productId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(2L);
        }

        @Test
        @DisplayName("성공 - 존재하지 않는 카운트 조회 시 빈 값 반환")
        void 미존재_카운트_조회() {
            // Arrange
            Long productId = 999L;

            // Act
            Optional<Long> result = likeCountCacheService.get(productId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getMultiple 테스트")
    class GetMultiple {

        @Test
        @DisplayName("성공 - 여러 상품 카운트 일괄 조회")
        void 일괄_조회() {
            // Arrange
            likeCountCacheService.set(1L, 10L);
            likeCountCacheService.set(2L, 20L);
            likeCountCacheService.set(3L, 30L);

            // Act
            Map<Long, Long> result = likeCountCacheService.getMultiple(List.of(1L, 2L, 3L, 4L));

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result.get(1L)).isEqualTo(10L);
            assertThat(result.get(2L)).isEqualTo(20L);
            assertThat(result.get(3L)).isEqualTo(30L);
            assertThat(result.containsKey(4L)).isFalse();
        }

        @Test
        @DisplayName("성공 - 다른 샤드에 있는 상품들도 조회됨")
        void 다른_샤드_조회() {
            // Arrange - 16개 샤드에 분산되도록 productId 설정
            likeCountCacheService.set(0L, 100L);   // shard 0
            likeCountCacheService.set(16L, 116L);  // shard 0
            likeCountCacheService.set(1L, 101L);   // shard 1
            likeCountCacheService.set(15L, 115L);  // shard 15

            // Act
            Map<Long, Long> result = likeCountCacheService.getMultiple(List.of(0L, 1L, 15L, 16L));

            // Assert
            assertThat(result).hasSize(4);
            assertThat(result.get(0L)).isEqualTo(100L);
            assertThat(result.get(16L)).isEqualTo(116L);
            assertThat(result.get(1L)).isEqualTo(101L);
            assertThat(result.get(15L)).isEqualTo(115L);
        }
    }

    @Nested
    @DisplayName("set 테스트")
    class SetValue {

        @Test
        @DisplayName("성공 - 카운트 직접 설정")
        void 카운트_설정() {
            // Arrange
            Long productId = 1L;
            Long count = 100L;

            // Act
            likeCountCacheService.set(productId, count);

            // Assert
            Optional<Long> result = likeCountCacheService.get(productId);
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(100L);
        }

        @Test
        @DisplayName("성공 - Overwrite - 기존 값 덮어쓰기")
        void 덮어쓰기() {
            // Arrange
            Long productId = 1L;
            likeCountCacheService.set(productId, 50L);

            // Act - 덮어쓰기
            likeCountCacheService.set(productId, 200L);

            // Assert
            Optional<Long> result = likeCountCacheService.get(productId);
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(200L);
        }
    }

    @Nested
    @DisplayName("setMultiple 테스트")
    class SetMultiple {

        @Test
        @DisplayName("성공 - 여러 상품 카운트 일괄 설정")
        void 일괄_설정() {
            // Arrange
            Map<Long, Long> counts = Map.of(1L, 10L, 2L, 20L, 3L, 30L);

            // Act
            likeCountCacheService.setMultiple(counts);

            // Assert
            assertThat(likeCountCacheService.get(1L)).contains(10L);
            assertThat(likeCountCacheService.get(2L)).contains(20L);
            assertThat(likeCountCacheService.get(3L)).contains(30L);
        }
    }

    @Nested
    @DisplayName("markDirty/getDirtyProductIds 테스트")
    class DirtyTracking {

        @Test
        @DisplayName("성공 - dirty 상품 ID 추적")
        void dirty_상품_추적() {
            // Arrange
            likeCountCacheService.markDirty(1L);
            likeCountCacheService.markDirty(2L);
            likeCountCacheService.markDirty(1L); // 중복

            // Act
            Set<Long> dirtyIds = likeCountCacheService.getDirtyProductIds();

            // Assert
            assertThat(dirtyIds).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("성공 - dirty 플래그 초기화")
        void dirty_플래그_초기화() {
            // Arrange
            likeCountCacheService.markDirty(1L);
            likeCountCacheService.markDirty(2L);

            // Act
            likeCountCacheService.clearDirtyProductIds();
            Set<Long> dirtyIds = likeCountCacheService.getDirtyProductIds();

            // Assert
            assertThat(dirtyIds).isEmpty();
        }

        @Test
        @DisplayName("성공 - 개별 dirty 플래그 제거")
        void 개별_dirty_제거() {
            // Arrange
            likeCountCacheService.markDirty(1L);
            likeCountCacheService.markDirty(2L);
            likeCountCacheService.markDirty(3L);

            // Act
            likeCountCacheService.removeDirty(2L);
            Set<Long> dirtyIds = likeCountCacheService.getDirtyProductIds();

            // Assert
            assertThat(dirtyIds).containsExactlyInAnyOrder(1L, 3L);
        }
    }

    @Nested
    @DisplayName("버저닝 테스트")
    class Versioning {

        @Test
        @DisplayName("성공 - 캐시 버전 확인")
        void 캐시_버전_확인() {
            // Act
            int version = likeCountCacheService.getCacheVersion();

            // Assert
            assertThat(version).isGreaterThanOrEqualTo(1);
        }
    }
}
