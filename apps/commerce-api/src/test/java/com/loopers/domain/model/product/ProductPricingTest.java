package com.loopers.domain.model.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPricingTest {

    @Test
    @DisplayName("정상가만 있는 경우 세일 아님")
    void notOnSale() {
        ProductPricing pricing = ProductPricing.of(Price.of(10000), null);

        assertThat(pricing.isOnSale()).isFalse();
        assertThat(pricing.getDiscountRate()).isEqualTo(0);
    }

    @Test
    @DisplayName("세일가 있는 경우 할인율 계산")
    void onSale_withDiscountRate() {
        ProductPricing pricing = ProductPricing.of(Price.of(139000), Price.of(99000));

        assertThat(pricing.isOnSale()).isTrue();
        assertThat(pricing.getDiscountRate()).isEqualTo(28);
    }

    @Test
    @DisplayName("가격 필수 검증")
    void price_required() {
        assertThatThrownBy(() -> ProductPricing.of(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품 가격은 필수입니다");
    }
}
