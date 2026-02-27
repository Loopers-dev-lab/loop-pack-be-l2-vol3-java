package com.loopers.domain.like;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeServiceUnitTest {

    private LikeService likeService;
    private FakeLikeRepository fakeLikeRepository;

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        likeService = new LikeService(fakeLikeRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class LikeRegister {

        @DisplayName("처음 좋아요하면, Like가 저장된다.")
        @Test
        void savesLike_whenFirstTime() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;

            // act
            Like result = likeService.like(userId, productId);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getProductId()).isEqualTo(100L);
        }

        @DisplayName("이미 좋아요한 상태에서 다시 등록하면, 예외 없이 기존 Like를 반환한다.")
        @Test
        void returnsExistingLike_whenAlreadyLiked() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            likeService.like(userId, productId);

            // act
            Like result = likeService.like(userId, productId);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getProductId()).isEqualTo(100L);
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 존재하면, 삭제된다.")
        @Test
        void deletesLike_whenExists() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            likeService.like(userId, productId);

            // act
            likeService.unlike(userId, productId);

            // assert
            long count = fakeLikeRepository.countByProductId(productId);
            assertThat(count).isZero();
        }

        @DisplayName("좋아요가 없는 상태에서 취소하면, 예외 없이 성공한다.")
        @Test
        void doesNothing_whenNotLiked() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;

            // act & assert (예외가 발생하지 않으면 성공)
            likeService.unlike(userId, productId);

            long count = fakeLikeRepository.countByProductId(productId);
            assertThat(count).isZero();
        }
    }

    @DisplayName("상품별 좋아요 수를 조회할 때,")
    @Nested
    class CountByProduct {

        @DisplayName("좋아요가 여러 개 있으면, 정확한 수를 반환한다.")
        @Test
        void returnsCorrectCount_whenMultipleLikesExist() {
            // arrange
            Long productId = 100L;
            likeService.like(1L, productId);
            likeService.like(2L, productId);
            likeService.like(3L, productId);

            // act
            long count = likeService.countByProductId(productId);

            // assert
            assertThat(count).isEqualTo(3);
        }

        @DisplayName("좋아요가 없으면, 0을 반환한다.")
        @Test
        void returnsZero_whenNoLikesExist() {
            // act
            long count = likeService.countByProductId(999L);

            // assert
            assertThat(count).isZero();
        }
    }
}