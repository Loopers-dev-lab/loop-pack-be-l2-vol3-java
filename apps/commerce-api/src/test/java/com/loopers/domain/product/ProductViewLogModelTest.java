package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductViewLogModelTest {

    @DisplayName("조회 로그를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("상품 ID와 조회 시각이 주어지면, 정상적으로 생성된다.")
        @Test
        void create_whenAllDataProvided() {
            // arrange
            ZonedDateTime viewedAt = ZonedDateTime.now();

            // act
            ProductViewLogModel viewLog = ProductViewLogModel.create(1L, 100L, viewedAt);

            // assert
            assertAll(
                    () -> assertThat(viewLog.getProductId()).isEqualTo(1L),
                    () -> assertThat(viewLog.getUserId()).isEqualTo(100L),
                    () -> assertThat(viewLog.getViewedAt()).isEqualTo(viewedAt));
        }

        @DisplayName("상품 ID가 null이면, 예외가 발생한다.")
        @Test
        void create_whenProductIdIsNull() {
            assertThatThrownBy(() ->
                    ProductViewLogModel.create(null, 100L, ZonedDateTime.now()))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품 ID는 필수값입니다.");
        }
    }
}
