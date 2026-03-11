package com.loopers.domain.like;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

public class LikeTest {

    @DisplayName("좋아요의 상품 정보가 유효하지 않으면, 예외를 던진다")
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-100L})
    void fail_when_invalid_ref_product_id(Long refProductId) {
        Long id = 100L;
        Long refUserId = 100L;

        assertThatThrownBy(() -> Like.create(id, refProductId, refUserId))
            .isInstanceOf(CoreException.class)
            .hasMessage("상품FK는 null이거나 음수가 될 수 없습니다");
    }

    @DisplayName("좋아요의 유저 정보가 유효하지 않으면, 예외를 던진다")
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {-100L})
    void fail_when_invalid_ref_user_id(Long refUserId) {
        Long id = 100L;
        Long refProductId = 100L;

        assertThatThrownBy(() -> Like.create(id, refProductId, refUserId))
            .isInstanceOf(CoreException.class)
            .hasMessage("유저FK는 null이거나 음수가 될 수 없습니다");
    }
}
