package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("LikeAppService 통합 테스트")
class LikeAppServiceIntegrationTest {

    @Autowired
    private LikeAppService likeAppService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("좋아요 등록/취소 흐름")
    class LikeFlowTest {

        @Test
        @DisplayName("좋아요를 등록하면 DB에 저장되고, 좋아요 수가 증가한다")
        void addLike_savesToDatabase() {
            // given
            Long userId = 1L;
            Long productId = 100L;

            // when
            Like like = likeAppService.addLike(userId, productId);

            // then
            assertThat(like.getId()).isNotNull();
            assertThat(likeJpaRepository.findByUserIdAndProductId(userId, productId)).isPresent();
            assertThat(likeAppService.countByProductId(productId)).isEqualTo(1L);
        }

        @Test
        @DisplayName("동일한 사용자가 같은 상품에 중복 좋아요를 시도하면 기존 좋아요를 반환한다")
        void addLike_duplicate_returnsExisting() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            Like firstLike = likeAppService.addLike(userId, productId);

            // when
            Like secondLike = likeAppService.addLike(userId, productId);

            // then
            assertThat(secondLike.getId()).isEqualTo(firstLike.getId());
            assertThat(likeAppService.countByProductId(productId)).isEqualTo(1L);
        }

        @Test
        @DisplayName("좋아요를 취소하면 DB에서 삭제되고, 좋아요 수가 감소한다")
        void removeLike_deletesFromDatabase() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            likeAppService.addLike(userId, productId);
            assertThat(likeAppService.countByProductId(productId)).isEqualTo(1L);

            // when
            likeAppService.removeLike(userId, productId);

            // then
            assertThat(likeJpaRepository.findByUserIdAndProductId(userId, productId)).isEmpty();
            assertThat(likeAppService.countByProductId(productId)).isEqualTo(0L);
        }

        @Test
        @DisplayName("좋아요 등록 후 좋아요 여부 확인이 true를 반환한다")
        void isLikedByUser_afterAdd_returnsTrue() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            likeAppService.addLike(userId, productId);

            // when
            boolean isLiked = likeAppService.isLikedByUser(userId, productId);

            // then
            assertThat(isLiked).isTrue();
        }

        @Test
        @DisplayName("좋아요 취소 후 좋아요 여부 확인이 false를 반환한다")
        void isLikedByUser_afterRemove_returnsFalse() {
            // given
            Long userId = 1L;
            Long productId = 100L;
            likeAppService.addLike(userId, productId);
            likeAppService.removeLike(userId, productId);

            // when
            boolean isLiked = likeAppService.isLikedByUser(userId, productId);

            // then
            assertThat(isLiked).isFalse();
        }

        @Test
        @DisplayName("여러 사용자가 같은 상품에 좋아요를 하면 좋아요 수가 누적된다")
        void addLike_multipleUsers_countsAccumulate() {
            // given
            Long productId = 100L;

            // when
            likeAppService.addLike(1L, productId);
            likeAppService.addLike(2L, productId);
            likeAppService.addLike(3L, productId);

            // then
            assertThat(likeAppService.countByProductId(productId)).isEqualTo(3L);
        }

        @Test
        @DisplayName("존재하지 않는 좋아요를 취소해도 예외가 발생하지 않는다")
        void removeLike_notExists_doesNotThrow() {
            // given
            Long userId = 1L;
            Long productId = 100L;

            // when & then (예외 없이 정상 동작)
            likeAppService.removeLike(userId, productId);
            assertThat(likeAppService.countByProductId(productId)).isEqualTo(0L);
        }
    }
}
