package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeTest {

    @DisplayName("Like 생성: 유효한 userId, productId로 생성된다")
    @Test
    void create_success() {
        Like like = Like.create(1L, 2L);

        assertThat(like.getUserId()).isEqualTo(1L);
        assertThat(like.getProductId()).isEqualTo(2L);
        assertThat(like.isDeleted()).isFalse();
    }

    @DisplayName("Like 생성: userId가 null이면 예외가 발생한다")
    @Test
    void create_fail_nullUserId() {
        assertThatThrownBy(() -> Like.create(null, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용자 ID는 필수입니다");
    }

    @DisplayName("Like 생성: userId가 0 이하이면 예외가 발생한다")
    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -100L})
    void create_fail_invalidUserId(Long invalidUserId) {
        assertThatThrownBy(() -> Like.create(invalidUserId, 1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("사용자 ID는 필수입니다");
    }

    @DisplayName("Like 생성: productId가 null이면 예외가 발생한다")
    @Test
    void create_fail_nullProductId() {
        assertThatThrownBy(() -> Like.create(1L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품 ID는 필수입니다");
    }

    @DisplayName("Like 생성: productId가 0 이하이면 예외가 발생한다")
    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -100L})
    void create_fail_invalidProductId(Long invalidProductId) {
        assertThatThrownBy(() -> Like.create(1L, invalidProductId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품 ID는 필수입니다");
    }

    @DisplayName("Like 삭제/복구: soft delete와 restore가 동작한다")
    @Test
    void delete_restore_success() {
        Like like = Like.create(1L, 2L);

        like.delete();
        assertThat(like.isDeleted()).isTrue();
        assertThat(like.getDeletedAt()).isNotNull();

        like.restore();
        assertThat(like.isDeleted()).isFalse();
        assertThat(like.getDeletedAt()).isNull();
    }

    @DisplayName("Like 삭제: delete는 멱등하게 동작한다")
    @Test
    void delete_idempotent() {
        Like like = Like.create(1L, 2L);

        like.delete();
        like.delete(); // 두 번 삭제해도 동일한 결과

        assertThat(like.isDeleted()).isTrue();
    }
}
