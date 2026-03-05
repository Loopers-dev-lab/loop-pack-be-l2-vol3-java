package com.loopers.domain.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.infrastructure.like.persistence.LikeJpaRepository;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.ConcurrentTestHelper;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

class LikeServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class LikeMethod {

        @DisplayName("유효한 요청이면, 좋아요가 저장되고 true를 반환한다.")
        @Test
        void savesLikeToDatabase_whenValidInputProvided() {
            // arrange
            var productId = createProduct(brandId);
            var userId = 1L;

            // act
            boolean result = likeService.like(userId, productId);

            // assert
            assertAll(
                    () -> assertThat(result).isTrue(),
                    () -> assertThat(likeJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("이미 좋아요가 존재하면, false를 반환하고 좋아요는 1개만 유지된다. (멱등성)")
        @Test
        void returnsFalse_whenLikeAlreadyExists() {
            // arrange
            var productId = createProduct(brandId);
            var userId = 1L;
            likeService.like(userId, productId);

            // act
            boolean result = likeService.like(userId, productId);

            // assert
            assertAll(
                    () -> assertThat(result).isFalse(),
                    () -> assertThat(likeJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("동일한 사용자가 동시에 좋아요를 요청하면, 하나만 성공하고 좋아요는 1개만 생성된다.")
        @Test
        void onlyOneLikeCreated_whenConcurrentLikeRequests() throws InterruptedException {
            // arrange
            var productId = createProduct(brandId);
            var userId = 1L;
            int threadCount = 10;

            // act
            var result = ConcurrentTestHelper.executeConcurrently(
                    threadCount,
                    () -> likeService.like(userId, productId)
            );

            // assert
            assertThat(result.successCount() + result.failCount()).isEqualTo(threadCount);
            assertThat(likeJpaRepository.count()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 여부를 확인할 때,")
    @Nested
    class IsLiked {

        @DisplayName("좋아요가 존재하면, true를 반환한다.")
        @Test
        void returnsTrue_whenLikeExists() {
            // arrange
            var productId = createProduct(brandId);
            likeService.like(1L, productId);

            // act & assert
            assertThat(likeService.isLiked(1L, productId)).isTrue();
        }

        @DisplayName("좋아요가 존재하지 않으면, false를 반환한다.")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            // arrange
            var productId = createProduct(brandId);

            // act & assert
            assertThat(likeService.isLiked(1L, productId)).isFalse();
        }

        @DisplayName("userId가 null이면, false를 반환한다.")
        @Test
        void returnsFalse_whenUserIdIsNull() {
            // act & assert
            assertThat(likeService.isLiked(null, 1L)).isFalse();
        }
    }

    @DisplayName("좋아요 목록을 조회할 때,")
    @Nested
    class GetLikes {

        @DisplayName("좋아요한 상품이 있으면, Like 목록이 반환된다.")
        @Test
        void returnsLikes_whenUserHasLikes() {
            // arrange
            var productId = createProduct(brandId);
            likeService.like(1L, productId);

            // act
            Page<Like> result = likeService.getLikes(1L, new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(1),
                    () -> assertThat(result.content().get(0).getProductId()).isEqualTo(productId),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenUserHasNoLikes() {
            // act
            Page<Like> result = likeService.getLikes(1L, new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("좋아요한 상품이 페이지 크기보다 많으면, hasNext가 true이다.")
        @Test
        void supportsPagination_whenMultipleProductsLiked() {
            // arrange
            var userId = 1L;
            var productId1 = createProduct(brandId, "상품 1", 10000L, 100L);
            var productId2 = createProduct(brandId, "상품 2", 10000L, 100L);
            var productId3 = createProduct(brandId, "상품 3", 10000L, 100L);
            likeService.like(userId, productId1);
            likeService.like(userId, productId2);
            likeService.like(userId, productId3);

            // act
            Page<Like> result = likeService.getLikes(userId, new PageSize(0, 2));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.hasNext()).isTrue()
            );
        }
    }

    @DisplayName("좋아요한 상품 ID 목록을 조회할 때,")
    @Nested
    class GetLikedProductIds {

        @DisplayName("좋아요한 상품이 있으면, 해당 상품 ID가 반환된다.")
        @Test
        void returnsLikedProductIds_whenUserHasLikes() {
            // arrange
            var productId1 = createProduct(brandId, "상품 1", 10000L, 100L);
            var productId2 = createProduct(brandId, "상품 2", 10000L, 100L);
            likeService.like(1L, productId1);

            // act
            Set<Long> result = likeService.getLikedProductIds(1L, List.of(productId1, productId2));

            // assert
            assertAll(
                    () -> assertThat(result).containsExactly(productId1),
                    () -> assertThat(result).doesNotContain(productId2)
            );
        }

        @DisplayName("userId가 null이면, 빈 Set이 반환된다.")
        @Test
        void returnsEmptySet_whenUserIdIsNull() {
            // act
            Set<Long> result = likeService.getLikedProductIds(null, List.of(1L, 2L));

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class UnlikeMethod {

        @DisplayName("유효한 요청이면, 좋아요가 삭제되고 true를 반환한다.")
        @Test
        void deletesLikeFromDatabase_whenValidInputProvided() {
            // arrange
            var productId = createProduct(brandId);
            var userId = 1L;
            likeService.like(userId, productId);

            // act
            boolean result = likeService.unlike(userId, productId);

            // assert
            assertAll(
                    () -> assertThat(result).isTrue(),
                    () -> assertThat(likeJpaRepository.count()).isZero()
            );
        }

        @DisplayName("좋아요가 존재하지 않으면, false를 반환한다. (멱등성)")
        @Test
        void returnsFalse_whenLikeDoesNotExist() {
            // arrange
            var productId = createProduct(brandId);
            var userId = 1L;

            // act
            boolean result = likeService.unlike(userId, productId);

            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("상품별 좋아요를 삭제할 때,")
    @Nested
    class DeleteLikesByProductId {

        @DisplayName("해당 상품의 좋아요가 모두 삭제된다.")
        @Test
        void deletesAllLikesForProduct() {
            // arrange
            var productId = createProduct(brandId);
            likeService.like(1L, productId);
            likeService.like(2L, productId);

            // act
            likeService.deleteLikesByProductId(productId);

            // assert
            assertThat(likeJpaRepository.count()).isZero();
        }
    }

    @DisplayName("여러 상품의 좋아요를 삭제할 때,")
    @Nested
    class DeleteLikesByProductIds {

        @DisplayName("해당 상품들의 좋아요가 모두 삭제된다.")
        @Test
        void deletesAllLikesForProducts() {
            // arrange
            var productId1 = createProduct(brandId, "상품 1", 10000L, 100L);
            var productId2 = createProduct(brandId, "상품 2", 10000L, 100L);
            likeService.like(1L, productId1);
            likeService.like(1L, productId2);

            // act
            likeService.deleteLikesByProductIds(List.of(productId1, productId2));

            // assert
            assertThat(likeJpaRepository.count()).isZero();
        }

        @DisplayName("빈 목록이면, 아무 동작 없이 성공한다.")
        @Test
        void doesNothing_whenEmptyList() {
            // act & assert
            assertThatCode(() -> likeService.deleteLikesByProductIds(List.of()))
                    .doesNotThrowAnyException();
        }
    }
}
