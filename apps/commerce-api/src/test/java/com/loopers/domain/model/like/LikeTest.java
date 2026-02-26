package com.loopers.domain.model.like;

import com.loopers.domain.model.user.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeTest {

    @Test
    @DisplayName("좋아요 생성 성공")
    void create_success() {
        UserId userId = UserId.of("testuser1");
        Like like = Like.create(userId, 1L);

        assertThat(like.getId()).isNull();
        assertThat(like.getUserId()).isEqualTo(userId);
        assertThat(like.getProductId()).isEqualTo(1L);
        assertThat(like.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("userId null이면 예외")
    void create_fail_null_userId() {
        assertThatThrownBy(() -> Like.create(null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("사용자 ID는 필수입니다.");
    }

    @Test
    @DisplayName("productId null이면 예외")
    void create_fail_null_productId() {
        assertThatThrownBy(() -> Like.create(UserId.of("testuser1"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("상품 ID는 필수입니다.");
    }

    @Test
    @DisplayName("reconstitute로 DB에서 복원")
    void reconstitute_success() {
        LocalDateTime now = LocalDateTime.now();
        UserId userId = UserId.of("testuser1");
        Like like = Like.reconstitute(1L, userId, 100L, now);

        assertThat(like.getId()).isEqualTo(1L);
        assertThat(like.getUserId()).isEqualTo(userId);
        assertThat(like.getProductId()).isEqualTo(100L);
        assertThat(like.getCreatedAt()).isEqualTo(now);
    }
}
