package com.loopers.domain.like;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ProductRepository productRepository;

    private LikeService likeService;

    private BrandModel brand;
    private ProductModel product;

    @BeforeEach
    void setUp() {
        likeService = new LikeService(likeRepository, productRepository);
        brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
        product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
    }

    @DisplayName("좋아요를 할 때, ")
    @Nested
    class Like {

        @DisplayName("정상적인 정보가 주어지면, 좋아요가 생성된다.")
        @Test
        void createsLike_whenValidInfoIsProvided() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());
            given(likeRepository.save(any(LikeModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            LikeModel result = likeService.like(userId, productId);

            // assert
            assertAll(
                () -> assertThat(result.getUserId()).isEqualTo(userId),
                () -> assertThat(result.getProduct()).isEqualTo(product)
            );
            verify(likeRepository).save(any(LikeModel.class));
        }

        @DisplayName("이미 좋아요한 상품이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenAlreadyLiked() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            LikeModel existingLike = new LikeModel(userId, product);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(existingLike));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                likeService.like(userId, productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("동시 좋아요로 DB 유니크 충돌이 발생하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenDataIntegrityViolationOccurs() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());
            given(likeRepository.save(any(LikeModel.class))).willThrow(new DataIntegrityViolationException("Duplicate entry"));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                likeService.like(userId, productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // arrange
            Long userId = 1L;
            Long productId = 999L;
            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                likeService.like(userId, productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 존재하면, 좋아요가 삭제된다.")
        @Test
        void deletesLike_whenLikeExists() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            LikeModel like = new LikeModel(userId, product);
            given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.of(like));

            // act
            likeService.unlike(userId, productId);

            // assert
            verify(likeRepository).delete(like);
        }

        @DisplayName("좋아요가 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenLikeDoesNotExist() {
            // arrange
            Long userId = 1L;
            Long productId = 1L;
            given(likeRepository.findByUserIdAndProductId(userId, productId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                likeService.unlike(userId, productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내가 좋아요한 상품을 조회할 때, ")
    @Nested
    class GetMyLikes {

        @DisplayName("userId가 주어지면, 해당 유저의 좋아요 목록을 반환한다.")
        @Test
        void returnsLikes_whenUserIdIsProvided() {
            // arrange
            Long userId = 1L;
            List<LikeModel> likes = List.of(
                new LikeModel(userId, product)
            );
            given(likeRepository.findByUserId(userId)).willReturn(likes);

            // act
            List<LikeModel> result = likeService.getMyLikes(userId);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).getUserId()).isEqualTo(userId)
            );
        }
    }

    @DisplayName("좋아요 수를 조회할 때, ")
    @Nested
    class GetLikeCount {

        @DisplayName("상품 ID가 주어지면, 좋아요 수를 반환한다.")
        @Test
        void returnsLikeCount_whenProductIdIsProvided() {
            // arrange
            Long productId = 1L;
            given(likeRepository.countByProductId(productId)).willReturn(5L);

            // act
            long result = likeService.getLikeCount(productId);

            // assert
            assertThat(result).isEqualTo(5L);
        }

        @DisplayName("상품 ID 목록이 주어지면, 각 상품의 좋아요 수를 반환한다.")
        @Test
        void returnsLikeCounts_whenProductIdsAreProvided() {
            // arrange
            List<Long> productIds = List.of(1L, 2L, 3L);
            Map<Long, Long> counts = Map.of(1L, 5L, 2L, 3L, 3L, 0L);
            given(likeRepository.countByProductIds(productIds)).willReturn(counts);

            // act
            Map<Long, Long> result = likeService.getLikeCountsByProductIds(productIds);

            // assert
            assertAll(
                () -> assertThat(result.get(1L)).isEqualTo(5L),
                () -> assertThat(result.get(2L)).isEqualTo(3L),
                () -> assertThat(result.get(3L)).isEqualTo(0L)
            );
        }
    }
}
