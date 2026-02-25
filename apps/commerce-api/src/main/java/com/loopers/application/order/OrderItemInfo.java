package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문 항목 응답용 애플리케이션 DTO.
 */
public record OrderItemInfo(
    Long productId,
    String productNameSnapshot,
    BigDecimal priceSnapshot,
    int quantity,
    Long optionId
) {
    public static OrderItemInfo from(OrderItemModel item) {
        if (item == null) {
            return null;
        }
        return new OrderItemInfo(
            item.getProductId(),
            item.getProductNameSnapshot(),
            item.getPriceSnapshot(),
            item.getQuantity(),
            item.getOptionId()
        );
    }
}
