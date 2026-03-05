package com.loopers.application.order;

import java.util.List;

public record OrderCreateCommand(
        List<Item> items,
        Long userCouponId  // nullable. 쿠폰 미적용 시 null (BR-O09)
) {

    public record Item(
            Long productId,
            int quantity
    ) {}
}
