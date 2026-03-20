package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderItemModel 도메인 모델 테스트")
class OrderItemModelTest {

    @Test
    @DisplayName("유효한 입력으로 생성 및 스냅샷 캡처")
    void create_WithValidInputs_ShouldCaptureSnapshot() {
        OrderItemModel item = OrderItemModel.create(
                1L, 1, 1L, 1L, 2,
                "테스트 상품", BigDecimal.valueOf(10000),
                "brand-001", "루퍼스", "img.jpg"
        );

        assertThat(item.getOrderId()).isEqualTo(1L);
        assertThat(item.getOrderItemSeq()).isEqualTo(1);
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getSnapshotProductName()).isEqualTo("테스트 상품");
        assertThat(item.getSnapshotUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(item.getSnapshotBrandId()).isEqualTo("brand-001");
        assertThat(item.getSnapshotBrandName()).isEqualTo("루퍼스");
        assertThat(item.getSnapshotImageUrl()).isEqualTo("img.jpg");
    }

    @Test
    @DisplayName("수량이 0이면 CoreException 발생")
    void create_WithZeroQuantity_ShouldThrow() {
        assertThatThrownBy(() -> OrderItemModel.create(
                1L, 1, 1L, 1L, 0,
                "상품", BigDecimal.valueOf(10000), "b-001", "브랜드", "img.jpg"
        )).isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("getSubtotal = unitPrice * quantity")
    void getSubtotal_ShouldReturn_unitPrice_times_quantity() {
        OrderItemModel item = OrderItemModel.create(
                1L, 1, 1L, 1L, 3,
                "상품", BigDecimal.valueOf(10000), "b-001", "브랜드", "img.jpg"
        );

        assertThat(item.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }
}
