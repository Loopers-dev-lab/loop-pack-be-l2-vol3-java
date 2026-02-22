package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product 도메인 테스트")
class ProductTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 정보로 상품을 생성할 수 있다")
        void createProduct() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L));

            assertThat(product.getId()).isNull();
            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("테스트 상품");
            assertThat(product.getBasePrice().getAmount()).isEqualByComparingTo("10000");
            assertThat(product.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("상품명이 빈 값이면 예외가 발생한다")
        void createWithEmptyNameThrowsException() {
            assertThatThrownBy(() -> Product.create(1L, "", Money.of(10000L)))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("수정 테스트")
    class UpdateTest {

        @Test
        @DisplayName("상품 정보를 수정할 수 있다")
        void updateProduct() {
            Product product = Product.create(1L, "원래 상품명", Money.of(10000L));

            product.update("새 상품명", Money.of(20000L));

            assertThat(product.getName()).isEqualTo("새 상품명");
            assertThat(product.getBasePrice().getAmount()).isEqualByComparingTo("20000");
        }

        @Test
        @DisplayName("수정 시 상품명이 빈 값이면 예외가 발생한다")
        void updateWithEmptyNameThrowsException() {
            Product product = Product.create(1L, "원래 상품명", Money.of(10000L));

            assertThatThrownBy(() -> product.update("", Money.of(20000L)))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("삭제 테스트")
    class DeleteTest {

        @Test
        @DisplayName("상품을 삭제할 수 있다")
        void deleteProduct() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L));

            product.delete();

            assertThat(product.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("삭제된 상품을 복원할 수 있다")
        void restoreProduct() {
            Product product = Product.create(1L, "테스트 상품", Money.of(10000L));
            product.delete();

            product.restore();

            assertThat(product.isDeleted()).isFalse();
        }
    }
}
