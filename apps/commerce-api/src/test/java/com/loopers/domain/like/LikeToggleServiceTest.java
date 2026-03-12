package com.loopers.domain.like;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeToggleServiceTest {

    private LikeToggleService likeToggleService;

    @BeforeEach
    void setUp() {
        likeToggleService = new LikeToggleService();
    }

    @DisplayName("좋아요 토글 - like")
    @Nested
    class Like {

        @DisplayName("좋아요가 없으면 새로 생성하고 countChanged=true를 반환한다")
        @Test
        void createsNewLikeWhenNoneExists() {
            // given
            Optional<LikeModel> existing = Optional.empty();

            // when
            LikeResult result = likeToggleService.like(existing, 1L, 100L);

            // then
            assertAll(
                () -> assertThat(result.newLike()).isPresent(),
                () -> assertThat(result.newLike().get().userId()).isEqualTo(1L),
                () -> assertThat(result.newLike().get().productId()).isEqualTo(100L),
                () -> assertThat(result.countChanged()).isTrue()
            );
        }

        @DisplayName("삭제된 좋아요가 있으면 복구하고 countChanged=true를 반환한다")
        @Test
        void restoresDeletedLike() {
            // given
            LikeModel deletedLike = new LikeModel(1L, 100L);
            deletedLike.delete();
            Optional<LikeModel> existing = Optional.of(deletedLike);

            // when
            LikeResult result = likeToggleService.like(existing, 1L, 100L);

            // then
            assertAll(
                () -> assertThat(result.newLike()).isEmpty(),
                () -> assertThat(result.countChanged()).isTrue(),
                () -> assertThat(deletedLike.getDeletedAt()).isNull()
            );
        }

        @DisplayName("이미 활성 좋아요가 있으면 아무것도 하지 않는다 (멱등)")
        @Test
        void skipsWhenAlreadyActive() {
            // given
            LikeModel activeLike = new LikeModel(1L, 100L);
            Optional<LikeModel> existing = Optional.of(activeLike);

            // when
            LikeResult result = likeToggleService.like(existing, 1L, 100L);

            // then
            assertAll(
                () -> assertThat(result.newLike()).isEmpty(),
                () -> assertThat(result.countChanged()).isFalse()
            );
        }
    }

    @DisplayName("좋아요 토글 - unlike")
    @Nested
    class Unlike {

        @DisplayName("활성 좋아요를 삭제한다")
        @Test
        void deletesLike() {
            // given
            LikeModel activeLike = new LikeModel(1L, 100L);

            // when
            likeToggleService.unlike(activeLike);

            // then
            assertThat(activeLike.getDeletedAt()).isNotNull();
        }
    }
}
