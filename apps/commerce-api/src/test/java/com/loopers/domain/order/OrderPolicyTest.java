package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderPolicyTest {

    @DisplayName("중복 상품 검증할 때, ")
    @Nested
    class ValidateNoDuplicateProducts {

        @DisplayName("중복이 없으면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenNoDuplicates() {
            List<Long> productIds = List.of(1L, 2L, 3L);

            assertThatCode(() -> OrderPolicy.validateNoDuplicateProducts(productIds))
                .doesNotThrowAnyException();
        }

        @DisplayName("중복된 상품 ID가 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenDuplicateProductIds() {
            List<Long> productIds = List.of(1L, 2L, 1L);

            CoreException result = assertThrows(CoreException.class,
                () -> OrderPolicy.validateNoDuplicateProducts(productIds));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("단일 상품이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenSingleProduct() {
            List<Long> productIds = List.of(1L);

            assertThatCode(() -> OrderPolicy.validateNoDuplicateProducts(productIds))
                .doesNotThrowAnyException();
        }
    }
}
