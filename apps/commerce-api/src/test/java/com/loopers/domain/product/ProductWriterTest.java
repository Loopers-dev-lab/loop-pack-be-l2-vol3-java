package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.shared.cache.CacheRepository;
import com.loopers.domain.shared.cache.CacheType;

@ExtendWith(MockitoExtension.class)
class ProductWriterTest {

    @InjectMocks
    private ProductWriter productWriter;

    @Mock
    private ProductService productService;

    @Mock
    private CacheRepository cacheRepository;

    @DisplayName("상품을 수정할 때,")
    @Nested
    class Update {

        @DisplayName("DB를 수정하고 상세 캐시를 overwrite한다. 목록 캐시(ID 리스트)는 evict하지 않는다.")
        @Test
        void updatesAndOverwritesDetailCache() {
            // arrange
            var modifyProduct = new ModifyProduct(1L, "수정된 상품명", "http://example.com/new.jpg", 20000L, 100L, "설명");
            var updatedProduct = Product.create(new ProductSpec(1L, "수정된 상품명", "http://example.com/new.jpg", 20000L, 100L, "설명"));
            given(productService.update(modifyProduct)).willReturn(updatedProduct);

            // act
            productWriter.update(modifyProduct);

            // assert
            String detailKey = ProductCacheConstants.DETAIL_KEY.of(1L);
            then(cacheRepository).should().put(eq(detailKey), eq(updatedProduct), any(Duration.class));
            then(cacheRepository).shouldHaveNoMoreInteractions();
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("삭제에 성공하면, 목록 캐시 전체 + 해당 상세 캐시를 무효화한다.")
        @Test
        void deletesAndEvictsCache() {
            // arrange
            Long productId = 1L;
            given(productService.delete(productId)).willReturn(true);

            // act
            boolean result = productWriter.delete(productId);

            // assert
            assertThat(result).isTrue();
            then(cacheRepository).should().evict(ProductCacheConstants.LIST_KEY.pattern());
            then(cacheRepository).should().evict(ProductCacheConstants.DETAIL_KEY.of(productId));
        }

        @DisplayName("이미 삭제된 상품이면, 캐시 무효화를 수행하지 않는다.")
        @Test
        void doesNotEvictCache_whenAlreadyDeleted() {
            // arrange
            Long productId = 1L;
            given(productService.delete(productId)).willReturn(false);

            // act
            boolean result = productWriter.delete(productId);

            // assert
            assertThat(result).isFalse();
            then(cacheRepository).shouldHaveNoInteractions();
        }
    }

    @DisplayName("좋아요 수를 증가시킬 때,")
    @Nested
    class IncreaseLikeCount {

        @DisplayName("DB를 갱신하고 상세 캐시의 likeCount를 Write-Through한다.")
        @Test
        void updatesDbAndWritesThroughCache() {
            // arrange
            Long productId = 1L;
            var product = Product.create(new ProductSpec(1L, "상품명", "http://example.com/thumb.jpg", 10000L, 50L, null));
            String detailKey = ProductCacheConstants.DETAIL_KEY.of(productId);
            given(cacheRepository.get(eq(detailKey), any(CacheType.class))).willReturn(product);

            // act
            productWriter.increaseLikeCount(productId);

            // assert
            then(productService).should().increaseLikeCount(productId);
            assertThat(product.getLikeCount()).isEqualTo(1);
            then(cacheRepository).should().put(eq(detailKey), eq(product), any(Duration.class));
        }

        @DisplayName("캐시 miss이면, DB만 갱신하고 캐시에 쓰지 않는다.")
        @Test
        void updatesDbOnly_whenCacheMiss() {
            // arrange
            Long productId = 1L;
            String detailKey = ProductCacheConstants.DETAIL_KEY.of(productId);
            given(cacheRepository.get(eq(detailKey), any(CacheType.class))).willReturn(null);

            // act
            productWriter.increaseLikeCount(productId);

            // assert
            then(productService).should().increaseLikeCount(productId);
            then(cacheRepository).should().get(eq(detailKey), any(CacheType.class));
            then(cacheRepository).shouldHaveNoMoreInteractions();
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때,")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("DB를 갱신하고 상세 캐시의 likeCount를 Write-Through한다.")
        @Test
        void updatesDbAndWritesThroughCache() {
            // arrange
            Long productId = 1L;
            var product = Product.create(new ProductSpec(1L, "상품명", "http://example.com/thumb.jpg", 10000L, 50L, null));
            // likeCount를 1로 만들어두고 감소 테스트
            product.adjustLikeCount(1);
            String detailKey = ProductCacheConstants.DETAIL_KEY.of(productId);
            given(cacheRepository.get(eq(detailKey), any(CacheType.class))).willReturn(product);

            // act
            productWriter.decreaseLikeCount(productId);

            // assert
            then(productService).should().decreaseLikeCount(productId);
            assertThat(product.getLikeCount()).isEqualTo(0);
            then(cacheRepository).should().put(eq(detailKey), eq(product), any(Duration.class));
        }
    }
}
