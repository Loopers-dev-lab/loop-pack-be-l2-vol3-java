package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeTest {

    @Nested
    class 생성 {

        @Test
        void 유효한_값이면_좋아요가_생성된다() {
            Like like = Like.create(1L, 1L);

            assertThat(like.getUserId()).isEqualTo(1L);
            assertThat(like.getProductId()).isEqualTo(1L);
        }

        @Test
        void 사용자ID가_null이면_예외() {
            assertThatThrownBy(() -> Like.create(null, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("사용자 ID는 필수입니다");
        }

        @Test
        void 상품ID가_null이면_예외() {
            assertThatThrownBy(() -> Like.create(1L, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("상품 ID는 필수입니다");
        }
    }
}
