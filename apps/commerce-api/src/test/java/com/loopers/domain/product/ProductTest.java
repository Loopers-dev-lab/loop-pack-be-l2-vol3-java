package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class ProductTest {

    @DisplayName("상품을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보이면, 정상 생성된다.")
        @ParameterizedTest(name = "description={0}")
        @NullSource
        @ValueSource(strings = {"상품 설명"})
        void success(String description) {
            // arrange
            var brandId = 1L;
            var name = "상품명";
            var thumbnailUrl = "http://example.com/thumbnail.jpg";
            var price = 10000L;
            var stock = 50L;

            // act
            var product = Product.create(brandId, name, thumbnailUrl, price, stock, description);

            // assert
            assertAll(
                    () -> assertThat(product.getBrandId()).isEqualTo(brandId),
                    () -> assertThat(product.getName()).isEqualTo(new ProductName(name)),
                    () -> assertThat(product.getThumbnailUrl()).isEqualTo(new ProductThumbnailUrl(thumbnailUrl)),
                    () -> assertThat(product.getPrice()).isEqualTo(Money.wons(price)),
                    () -> assertThat(product.getStock()).isEqualTo(new Stock(stock)),
                    () -> assertThat(product.getDescription()).isEqualTo(description)
            );
        }

        @DisplayName("브랜드 ID가 null이면, REQUIRED_BRAND_ID 에러가 발생한다.")
        @ParameterizedTest
        @NullSource
        void throwsException_whenBrandIdIsNull(Long brandId) {
            assertThatThrownBy(() -> Product.create(brandId, "상품명", "http://example.com/thumbnail.jpg", 10000L, 50L, "상품 설명"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining(ErrorType.REQUIRED_BRAND_ID.getMessage());
        }
    }
}
