package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.like.LikeEventPublisher;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LikeFacadeTest {

    UserRepository userRepository = mock(UserRepository.class);
    ProductRepository productRepository = mock(ProductRepository.class);
    LikeRepository likeRepository = mock(LikeRepository.class);
    BrandRepository brandRepository = mock(BrandRepository.class);
    LikeAssembler likeAssembler = new LikeAssembler();
    LikeEventPublisher likeEventPublisher = mock(LikeEventPublisher.class);

    LikeFacade likeFacade = new LikeFacade(userRepository, productRepository, likeRepository, brandRepository, likeAssembler, likeEventPublisher);

    @DisplayName("좋아요 시, ")
    @Nested
    class LikeTest {

        @DisplayName("사용자가 존재하지 않으면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenUserNotFound() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            when(userRepository.existsById(userId)).thenReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                likeFacade.like(userId, productId)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("존재하지 않는 사용자입니다.");
        }

        @DisplayName("상품이 존재하지 않으면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenProductNotFound() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            when(userRepository.existsById(userId)).thenReturn(true);
            when(productRepository.existsById(productId)).thenReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                likeFacade.like(userId, productId)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("존재하지 않는 상품입니다.");
        }

        @DisplayName("좋아요가 처음 등록되면, LikeEvent.Created 이벤트가 발행된다.")
        @Test
        void increasesLikeCount_whenLikeInserted() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            when(userRepository.existsById(userId)).thenReturn(true);
            when(productRepository.existsById(productId)).thenReturn(true);
            when(likeRepository.saveIfAbsent(any())).thenReturn(true);

            // act
            likeFacade.like(userId, productId);

            // assert
            verify(likeEventPublisher).publish(new LikeEvent.Created(userId, productId));
        }

        @DisplayName("이미 좋아요한 이력이 있으면, LikeEvent.Created 이벤트가 발행되지 않는다.")
        @Test
        void doesNotIncreaseLikeCount_whenLikeAlreadyExists() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            when(userRepository.existsById(userId)).thenReturn(true);
            when(productRepository.existsById(productId)).thenReturn(true);
            when(likeRepository.saveIfAbsent(any())).thenReturn(false);

            // act
            likeFacade.like(userId, productId);

            // assert
            verify(likeEventPublisher, never()).publish(new LikeEvent.Created(userId, productId));
        }
    }

    @DisplayName("좋아요 취소 시, ")
    @Nested
    class UnlikeTest {

        @DisplayName("좋아요가 존재해 삭제되면, LikeEvent.Deleted 이벤트 발행된다.")
        @Test
        void decreasesLikeCount_whenLikeDeleted() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            when(likeRepository.deleteByUserIdAndProductId(userId, productId)).thenReturn(1);

            // act
            likeFacade.unlike(userId, productId);

            // assert
            verify(likeEventPublisher).publish(new LikeEvent.Deleted(userId, productId));
        }

        @DisplayName("좋아요가 존재하지 않으면, LikeEvent.Deleted 이벤트가 발행되지 않는다.")
        @Test
        void doesNotDecreaseLikeCount_whenLikeNotExists() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            when(likeRepository.deleteByUserIdAndProductId(userId, productId)).thenReturn(0);

            // act
            likeFacade.unlike(userId, productId);

            // assert
            verify(likeEventPublisher, never()).publish(new LikeEvent.Deleted(userId, productId));
        }
    }

    @DisplayName("내가 좋아요한 상품 목록 조회 시, ")
    @Nested
    class GetLikeList {

        @DisplayName("좋아요한 상품이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // arrange
            Long userId = 1L;
            when(likeRepository.findAllByUserId(userId)).thenReturn(List.of());

            // act
            List<LikeProductInfo> result = likeFacade.getLikeList(userId);

            // assert
            assertThat(result).isEmpty();
        }

        @DisplayName("좋아요한 상품이 있으면 브랜드명과 좋아요 수가 포함된 목록을 반환한다.")
        @Test
        void returnsLikeProductInfoList_withBrandNameAndLikeCount() {
            // arrange
            Long userId = 1L;
            Long productId = 0L;
            Long brandId = 0L;

            Like like = Like.of(userId, productId);
            Product product = Product.of("상품명", "상품 설명", Stock.from(10), Price.from(1000), brandId);
            Brand brand = Brand.of("브랜드명", "브랜드 설명");

            when(likeRepository.findAllByUserId(userId)).thenReturn(List.of(like));
            when(productRepository.findAllByIdIn(List.of(productId))).thenReturn(List.of(product));
            when(brandRepository.findAllByIdIn(List.of(brandId))).thenReturn(List.of(brand));

            // act
            List<LikeProductInfo> result = likeFacade.getLikeList(userId);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("상품명");
            assertThat(result.get(0).brand()).isEqualTo("브랜드명");
            assertThat(result.get(0).likeCount()).isEqualTo(0L);
        }
    }
}
