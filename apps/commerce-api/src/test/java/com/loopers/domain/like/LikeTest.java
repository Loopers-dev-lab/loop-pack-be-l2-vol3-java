package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Like 도메인 테스트")
class LikeTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 정보로 좋아요를 생성할 수 있다")
        void createLike() {
            Like like = Like.create(1L, 100L);

            assertThat(like.getId()).isNull();
            assertThat(like.getUserId()).isEqualTo(1L);
            assertThat(like.getProductId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("ID를 포함하여 좋아요 객체를 복원할 수 있다")
        void createLikeWithId() {
            Like like = Like.of(10L, 1L, 100L);

            assertThat(like.getId()).isEqualTo(10L);
            assertThat(like.getUserId()).isEqualTo(1L);
            assertThat(like.getProductId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("사용자 ID가 없으면 예외가 발생한다")
        void createWithNullUserIdThrowsException() {
            assertThatThrownBy(() -> Like.create(null, 100L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("사용자 ID는 필수");
        }

        @Test
        @DisplayName("상품 ID가 없으면 예외가 발생한다")
        void createWithNullProductIdThrowsException() {
            assertThatThrownBy(() -> Like.create(1L, null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품 ID는 필수");
        }
    }

    @Nested
    @DisplayName("불변성 테스트")
    class ImmutabilityTest {

        @Test
        @DisplayName("좋아요 객체는 생성 후 상태가 변하지 않는다")
        void likeIsImmutable() {
            Like like = Like.create(1L, 100L);

            assertThat(like.getUserId()).isEqualTo(1L);
            assertThat(like.getProductId()).isEqualTo(100L);
        }
    }
}
