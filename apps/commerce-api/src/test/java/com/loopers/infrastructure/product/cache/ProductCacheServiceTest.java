package com.loopers.infrastructure.product.cache;

import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.repository.ProductCacheRepository;
import com.loopers.domain.product.vo.DisplayStatus;
import com.loopers.support.cache.RedisCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheRepositoryImplTest {

    @InjectMocks
    private ProductCacheRepositoryImpl productCacheRepository;

    @Mock
    private RedisCacheManager redisCacheManager;

    private static ProductCacheDto createTestCacheDto() {
        return new ProductCacheDto(1L, "상품A", 1L, "나이키", 10000, 100, DisplayStatus.DISPLAYING);
    }

    private static ProductItem createTestProductItem() {
        return new ProductItem(1L, "상품A", 1L, "나이키", 10000, 100, DisplayStatus.DISPLAYING, 5L, false, null);
    }

    @DisplayName("상품 캐시 조회")
    @Nested
    class Get {

        @DisplayName("캐시에 데이터가 있으면 ProductItem으로 변환하여 반환한다")
        @Test
        void returnsProductItem_whenCacheHit() {
            // arrange
            ProductCacheDto dto = createTestCacheDto();
            when(redisCacheManager.get("product:1", ProductCacheDto.class))
                    .thenReturn(Optional.of(dto));

            // act
            Optional<ProductItem> result = productCacheRepository.get(1L);

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(1L);
            assertThat(result.get().name()).isEqualTo("상품A");
            assertThat(result.get().brandName()).isEqualTo("나이키");
        }

        @DisplayName("캐시에 데이터가 없으면 빈 Optional을 반환한다")
        @Test
        void returnsEmpty_whenCacheMiss() {
            // arrange
            when(redisCacheManager.get("product:1", ProductCacheDto.class))
                    .thenReturn(Optional.empty());

            // act
            Optional<ProductItem> result = productCacheRepository.get(1L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("상품 캐시 저장")
    @Nested
    class Put {

        @DisplayName("ProductItem을 ProductCacheDto로 변환하여 Redis에 저장한다")
        @Test
        void convertsAndSaves() {
            // arrange
            ProductItem item = createTestProductItem();

            // act
            productCacheRepository.put(1L, item);

            // assert
            verify(redisCacheManager).put(eq("product:1"), any(ProductCacheDto.class), eq(3600L));
        }
    }

    @DisplayName("상품 캐시 삭제")
    @Nested
    class Evict {

        @DisplayName("RedisCacheManager에 삭제를 위임한다")
        @Test
        void delegatesToRedisCacheManager() {
            // act
            productCacheRepository.evict(1L);

            // assert
            verify(redisCacheManager).evict("product:1");
        }
    }

    @DisplayName("첫 페이지 캐시")
    @Nested
    class FirstPage {

        @DisplayName("캐시 히트 시 CachedPage로 변환하여 반환한다")
        @Test
        void returnsCachedPage_whenHit() {
            // arrange
            ProductCacheDto dto = createTestCacheDto();
            ProductListCacheDto listDto = new ProductListCacheDto(List.of(dto), 100);
            when(redisCacheManager.get("product-list:first-page", ProductListCacheDto.class))
                    .thenReturn(Optional.of(listDto));

            // act
            Optional<ProductCacheRepository.CachedPage> result = productCacheRepository.getFirstPage();

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().items()).hasSize(1);
            assertThat(result.get().totalElements()).isEqualTo(100);
        }

        @DisplayName("ProductItem 리스트를 ProductCacheDto로 변환하여 저장한다")
        @Test
        void convertsAndSavesFirstPage() {
            // arrange
            ProductItem item = createTestProductItem();

            // act
            productCacheRepository.putFirstPage(List.of(item), 100);

            // assert
            verify(redisCacheManager).put(eq("product-list:first-page"), any(ProductListCacheDto.class), eq(300L));
        }
    }

    @DisplayName("좋아요 카운터")
    @Nested
    class LikeCount {

        @DisplayName("카운터 초기화 시 Redis에 값을 설정한다")
        @Test
        void initSetsCount() {
            // act
            productCacheRepository.initLikeCountIfAbsent(1L, 5);

            // assert
            verify(redisCacheManager).setCountIfAbsent("product:1:likes", 5, 3600);
        }

        @DisplayName("카운터 증가 시 Redis increment를 호출한다")
        @Test
        void incrementDelegatesToRedis() {
            // act
            productCacheRepository.incrementLikeCount(1L);

            // assert
            verify(redisCacheManager).increment("product:1:likes");
        }

        @DisplayName("카운터 감소 시 Redis decrement를 호출한다")
        @Test
        void decrementDelegatesToRedis() {
            // act
            productCacheRepository.decrementLikeCount(1L);

            // assert
            verify(redisCacheManager).decrement("product:1:likes");
        }
    }
}
