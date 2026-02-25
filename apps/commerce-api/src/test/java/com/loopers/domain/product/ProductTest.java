package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Test
    @DisplayName("좋아요 감소 시 0 미만으로 내려가지 않는다")
    void decreaseLikeCountNotBelowZero() {
        Product product = new Product("사료", 1000, 10, "desc", 1L, 1L);

        Product decreased = product.decreaseLikeCount();

        assertThat(decreased.likeCount()).isZero();
    }

    @Test
    @DisplayName("가격이 음수면 예외가 발생한다")
    void negativePriceFails() {
        assertThatThrownBy(() -> new Product("사료", -1, 10, "desc", 1L, 1L))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
}
