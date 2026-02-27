package com.loopers.support.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductSaleStatus 열거형 테스트")
class ProductSaleStatusTest {

    @Test
    @DisplayName("ON_SALE, TEMP_SOLD_OUT, STOPPED 값이 존재한다")
    void values_ShouldContain_ON_SALE_TEMP_SOLD_OUT_STOPPED() {
        assertThat(ProductSaleStatus.values())
                .containsExactlyInAnyOrder(
                        ProductSaleStatus.ON_SALE,
                        ProductSaleStatus.TEMP_SOLD_OUT,
                        ProductSaleStatus.STOPPED
                );
    }

    @Test
    @DisplayName("ON_SALE일 때 isOrderable()은 true를 반환한다")
    void isOrderable_OnSale_ShouldReturnTrue() {
        assertThat(ProductSaleStatus.ON_SALE.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("TEMP_SOLD_OUT일 때 isOrderable()은 false를 반환한다")
    void isOrderable_TempSoldOut_ShouldReturnFalse() {
        assertThat(ProductSaleStatus.TEMP_SOLD_OUT.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("STOPPED일 때 isOrderable()은 false를 반환한다")
    void isOrderable_Stopped_ShouldReturnFalse() {
        assertThat(ProductSaleStatus.STOPPED.isOrderable()).isFalse();
    }
}
