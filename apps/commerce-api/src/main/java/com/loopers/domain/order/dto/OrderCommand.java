package com.loopers.domain.order.dto;

import com.loopers.domain.product.dto.ProductInfo;
import java.util.List;
import java.util.Map;

public class OrderCommand {

    public record CreateItem(Long productId, int price, int quantity,
                             String productName, String brandName) {

        public static List<CreateItem> from(
                List<ProductInfo.StockDeduction> deductions, Map<Long, String> brandNameMap) {
            return deductions.stream()
                    .map(info -> new CreateItem(
                            info.productId(), info.price(), info.quantity(),
                            info.name(), brandNameMap.get(info.brandId())))
                    .toList();
        }
    }
}
