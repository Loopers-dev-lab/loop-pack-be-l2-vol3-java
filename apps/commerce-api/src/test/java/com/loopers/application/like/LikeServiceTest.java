package com.loopers.application.like;

import com.loopers.domain.model.common.DomainEventPublisher;
import com.loopers.domain.model.like.Like;
import com.loopers.domain.model.product.*;
import com.loopers.domain.model.user.UserId;
import com.loopers.domain.repository.LikeRepository;
import com.loopers.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LikeServiceTest {

    private LikeRepository likeRepository;
    private ProductRepository productRepository;
    private DomainEventPublisher domainEventPublisher;
    private LikeService service;

    @BeforeEach
    void setUp() {
        likeRepository = mock(LikeRepository.class);
        productRepository = mock(ProductRepository.class);
        domainEventPublisher = mock(DomainEventPublisher.class);
        service = new LikeService(likeRepository, productRepository, domainEventPublisher);
    }

    @Nested
    @DisplayName("좋아요")
    class LikeTest {

        @Test
        @DisplayName("좋아요 성공")
        void like_success() {
            // given
            UserId userId = UserId.of("test1234");
            Product product = createProduct(1L, 0);

            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(userId, 1L)).thenReturn(false);

            // when
            service.like(userId, 1L);

            // then
            verify(likeRepository).save(any(Like.class));
            verify(domainEventPublisher).publishEvents(any(Like.class));
        }

        @Test
        @DisplayName("이미 좋아요한 경우 무시 (멱등성)")
        void like_alreadyLiked_ignored() {
            // given
            UserId userId = UserId.of("test1234");
            Product product = createProduct(1L, 1);

            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));
            when(likeRepository.existsByUserIdAndProductId(userId, 1L)).thenReturn(true);

            // when
            service.like(userId, 1L);

            // then
            verify(likeRepository, never()).save(any(Like.class));
            verify(domainEventPublisher, never()).publishEvents(any());
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요시 예외")
        void like_fail_productNotFound() {
            // given
            UserId userId = UserId.of("test1234");
            when(productRepository.findActiveById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.like(userId, 999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class UnlikeTest {

        @Test
        @DisplayName("좋아요 취소 성공")
        void unlike_success() {
            // given
            UserId userId = UserId.of("test1234");
            Product product = createProduct(1L, 1);
            Like like = Like.reconstitute(1L, userId, 1L, LocalDateTime.now());

            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));
            when(likeRepository.findByUserIdAndProductId(userId, 1L)).thenReturn(Optional.of(like));

            // when
            service.unlike(userId, 1L);

            // then
            verify(domainEventPublisher).publishEvents(any(Like.class));
            verify(likeRepository).deleteByUserIdAndProductId(userId, 1L);
        }

        @Test
        @DisplayName("좋아요하지 않은 경우 무시")
        void unlike_notLiked_ignored() {
            // given
            UserId userId = UserId.of("test1234");
            Product product = createProduct(1L, 0);

            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));
            when(likeRepository.findByUserIdAndProductId(userId, 1L)).thenReturn(Optional.empty());

            // when
            service.unlike(userId, 1L);

            // then
            verify(likeRepository, never()).deleteByUserIdAndProductId(any(), any());
            verify(domainEventPublisher, never()).publishEvents(any());
        }
    }

    private Product createProduct(Long id, int likeCount) {
        return Product.reconstitute(new ProductData(id, 1L, ProductName.of("상품" + id), Price.of(10000),
                null, Stock.of(100), likeCount, "설명",
                LocalDateTime.now(), LocalDateTime.now(), null));
    }
}
