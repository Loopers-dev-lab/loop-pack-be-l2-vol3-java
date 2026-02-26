package com.loopers.application.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderRequest() {

    // Command

    public record Place(
            @NotNull(message = "주문 상품 목록은 필수입니다")
            @Size(min = 1, max = 100, message = "주문 상품은 1~100건이어야 합니다")
            List<@Valid PlaceItem> orderItems
    ) {
    }

    public record PlaceItem(
            @NotNull(message = "상품 ID는 필수입니다")
            Long productId,

            @NotNull(message = "수량은 필수입니다")
            @Min(value = 1, message = "수량은 1 이상이어야 합니다")
            @Max(value = 9999999, message = "수량은 9,999,999 이하여야 합니다")
            Integer quantity
    ) {
    }
}
