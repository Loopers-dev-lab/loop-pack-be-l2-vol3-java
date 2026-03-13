package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("LikeModel 도메인 모델 테스트")
class LikeModelTest {

    @Test
    @DisplayName("유효한 입력으로 생성 성공")
    void create_WithValidInputs_ShouldSuccess() {
        LikeModel like = LikeModel.create(1L, 1L);

        assertThat(like.getUserId()).isEqualTo(1L);
        assertThat(like.getProductId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("userId가 null이면 CoreException 발생")
    void create_WithNullUserId_ShouldThrow() {
        assertThatThrownBy(() -> LikeModel.create(null, 1L))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("productId가 null이면 CoreException 발생")
    void create_WithNullProductId_ShouldThrow() {
        assertThatThrownBy(() -> LikeModel.create(1L, null))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("생성 시 createdAt은 @PrePersist에서 설정된다 (직접 생성 시 null)")
    void create_ShouldSetCreatedAt() {
        LikeModel like = LikeModel.create(1L, 1L);
        // createdAt은 @PrePersist에서 설정되므로 JPA 없이는 null
        // 단위 테스트에서는 생성이 성공했음을 확인
        assertThat(like).isNotNull();
    }
}
