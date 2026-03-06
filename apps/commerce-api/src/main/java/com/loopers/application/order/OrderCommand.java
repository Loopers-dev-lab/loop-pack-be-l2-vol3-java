package com.loopers.application.order;

import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record OrderCommand() {

    public record Place(List<PlaceItem> items, Long couponId) {
        public Place {
            long distinctCount = items.stream()
                    .map(PlaceItem::productId)
                    .distinct()
                    .count();
            if (distinctCount != items.size()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품이 중복되었습니다");
            }
        }

        public static Place of(List<PlaceItem> items) {
            return new Place(items, null);
        }

        public static Place of(List<PlaceItem> items, Long couponId) {
            return new Place(items, couponId);
        }

        public Map<Long, Integer> toQuantityMap() {
            return items.stream()
                    .collect(Collectors.toMap(PlaceItem::productId, PlaceItem::quantity));
        }

        public List<CreateItem> toCreateItems(List<Product> products) {
            Map<Long, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));
            return items.stream()
                    .map(item -> item.toCreateItem(productMap.get(item.productId())))
                    .toList();
        }
    }

    public record PlaceItem(Long productId, Integer quantity) {
        public static PlaceItem of(Long productId, Integer quantity) {
            return new PlaceItem(productId, quantity);
        }

        public CreateItem toCreateItem(Product product) {
            return CreateItem.of(product.getId(), product.getName(), product.getPrice(), quantity);
        }
    }

    public record Create(
            Long userId,
            List<CreateItem> items,
            CouponSnapshot coupon
    ) {
        public static Create of(Long userId, List<CreateItem> items) {
            return new Create(userId, items, null);
        }

        public static Create of(Long userId, List<CreateItem> items, CouponSnapshot coupon) {
            return new Create(userId, items, coupon);
        }
    }

    public record CreateItem(
            Long productId,
            String productName,
            BigDecimal price,
            int quantity
    ) {
        public static CreateItem of(Long productId, String productName,
                                         BigDecimal price, int quantity) {
            return new CreateItem(productId, productName, price, quantity);
        }

        public static BigDecimal calculateTotalAmount(List<CreateItem> items) {
            return items.stream()
                    .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record CouponSnapshot(
            Long issuedCouponId,
            BigDecimal discountAmount
    ) {
        public static CouponSnapshot of(Long issuedCouponId, BigDecimal discountAmount) {
            return new CouponSnapshot(issuedCouponId, discountAmount);
        }

    }
}
