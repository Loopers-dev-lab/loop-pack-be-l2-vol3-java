package com.loopers.application.product;

import com.loopers.domain.ranking.ProductRankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ProductCacheEvictListener 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProductCacheEvictListenerTest {

    @InjectMocks
    private ProductCacheEvictListener listener;

    @Mock
    private ProductDetailCacheRepository productDetailCacheRepository;

    @Mock
    private ProductListCacheRepository productListCacheRepository;

    @Mock
    private ProductRankingRepository productRankingRepository;

    @Test
    @DisplayName("상세 캐시와 목록 캐시를 모두 무효화하고 랭킹에서 제거한다")
    void evictsBothDetailAndListCacheAndRemovesFromRanking() {
        // Arrange
        ProductCacheEvictEvent event = ProductCacheEvictEvent.of(1L);

        // Act
        listener.handleCacheEvict(event);

        // Assert
        verify(productDetailCacheRepository).evict(1L);
        verify(productRankingRepository).removeProduct(1L);
        verify(productListCacheRepository).evictAll();
    }

    @Test
    @DisplayName("여러 상품의 상세 캐시를 무효화하고 랭킹에서 제거한다")
    void evictsMultipleDetailCachesAndRemovesFromRanking() {
        // Arrange
        ProductCacheEvictEvent event = ProductCacheEvictEvent.of(Set.of(1L, 2L, 3L));

        // Act
        listener.handleCacheEvict(event);

        // Assert
        verify(productDetailCacheRepository).evict(1L);
        verify(productDetailCacheRepository).evict(2L);
        verify(productDetailCacheRepository).evict(3L);
        verify(productRankingRepository).removeProduct(1L);
        verify(productRankingRepository).removeProduct(2L);
        verify(productRankingRepository).removeProduct(3L);
        verify(productListCacheRepository).evictAll();
    }

    @Test
    @DisplayName("listOnly 이벤트는 목록 캐시만 무효화한다")
    void evictsOnlyListCache_whenListOnly() {
        // Arrange
        ProductCacheEvictEvent event = ProductCacheEvictEvent.listOnly();

        // Act
        listener.handleCacheEvict(event);

        // Assert
        verify(productDetailCacheRepository, never()).evict(org.mockito.ArgumentMatchers.anyLong());
        verify(productRankingRepository, never()).removeProduct(org.mockito.ArgumentMatchers.anyLong());
        verify(productListCacheRepository).evictAll();
    }
}
