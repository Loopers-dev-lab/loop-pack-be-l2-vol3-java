package com.loopers.domain.product;

import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private Product createProduct() {
        return new Product(1L, "테스트 상품", new Price(10000), new Stock(10));
    }

    @Nested
    @DisplayName("Product 생성")
    class Create {

        @DisplayName("유효한 정보로 Product를 생성하면 필드가 올바르게 초기화된다")
        @Test
        void create_withValidInfo_fieldsAreInitialized() {
            Product product = createProduct();

            assertThat(product.getBrandId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("테스트 상품");
            assertThat(product.getPrice().getValue()).isEqualTo(10000);
            assertThat(product.getStock().getQuantity()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("재고 차감")
    class DecreaseStock {

        @DisplayName("재고가 충분하면 차감에 성공한다")
        @Test
        void decreaseStock_withSufficientStock_succeeds() {
            Product product = createProduct();

            product.decreaseStock(3);

            assertThat(product.getStock().getQuantity()).isEqualTo(7);
        }

        @DisplayName("재고가 부족하면 CoreException이 발생한다")
        @Test
        void decreaseStock_withInsufficientStock_throwsException() {
            Product product = createProduct();

            assertThatThrownBy(() -> product.decreaseStock(11))
                .isInstanceOf(CoreException.class);
        }
    }

}
