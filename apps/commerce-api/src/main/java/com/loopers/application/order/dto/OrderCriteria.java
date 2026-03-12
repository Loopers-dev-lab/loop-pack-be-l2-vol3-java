package com.loopers.application.order.dto;

import com.loopers.domain.product.dto.ProductCommand;
import java.time.ZonedDateTime;
import java.util.List;

public class OrderCriteria {

    public record Create(List<CreateItem> items, Long ownedCouponId) {

        public Create(List<CreateItem> items) {
            this(items, null);
        }

        public List<ProductCommand.StockDeduction> toStockDeductions() {
            return items.stream()
                    .map(item -> new ProductCommand.StockDeduction(
                            item.productId(), item.quantity(), item.expectedPrice()))
                    .toList();
        }

        public record CreateItem(Long productId, int quantity, int expectedPrice) {}
    }

    public record ListByDate(ZonedDateTime startAt, ZonedDateTime endAt) {}
}
