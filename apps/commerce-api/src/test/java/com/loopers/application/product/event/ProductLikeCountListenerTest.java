package com.loopers.application.product.event;

import com.loopers.application.like.event.ProductLikedEvent;
import com.loopers.application.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.product.ProductCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductLikeCountListenerTest {

    private ProductRepository productRepository;
    private ProductCacheStore productCacheStore;
    private ProductLikeCountListener listener;

    private final Long productId = 1L;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productCacheStore = mock(ProductCacheStore.class);
        listener = new ProductLikeCountListener(productRepository, productCacheStore);
    }

    @DisplayName("ProductLikedEvent 수신 시 상품의 likeCount를 1 증가시키고 캐시를 무효화한다")
    @Test
    void handleProductLiked_increasesLikeCount() {
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        listener.handleProductLiked(new ProductLikedEvent(productId));

        assertThat(product.getLikeCount()).isEqualTo(1);
        verify(productRepository).save(product);
        verify(productCacheStore).evictProduct(productId);
    }

    @DisplayName("ProductUnlikedEvent 수신 시 상품의 likeCount를 1 감소시키고 캐시를 무효화한다")
    @Test
    void handleProductUnliked_decreasesLikeCount() {
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        product.increaseLikeCount(); // likeCount = 1

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        listener.handleProductUnliked(new ProductUnlikedEvent(productId));

        assertThat(product.getLikeCount()).isZero();
        verify(productRepository).save(product);
        verify(productCacheStore).evictProduct(productId);
    }

    @DisplayName("ProductLikedEvent 수신 시 상품이 존재하지 않으면 예외를 로깅하고 정상 종료한다")
    @Test
    void handleProductLiked_productNotFound_logsWarning() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        listener.handleProductLiked(new ProductLikedEvent(productId));

        verify(productRepository, never()).save(any());
        verify(productCacheStore, never()).evictProduct(any());
    }

    @DisplayName("ProductUnlikedEvent 수신 시 상품이 존재하지 않으면 예외를 로깅하고 정상 종료한다")
    @Test
    void handleProductUnliked_productNotFound_logsWarning() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        listener.handleProductUnliked(new ProductUnlikedEvent(productId));

        verify(productRepository, never()).save(any());
        verify(productCacheStore, never()).evictProduct(any());
    }
}
