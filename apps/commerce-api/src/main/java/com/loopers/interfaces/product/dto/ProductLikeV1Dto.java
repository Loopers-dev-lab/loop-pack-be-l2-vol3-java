package com.loopers.interfaces.product.dto;

import com.loopers.application.product.dto.ProductLikeResult;
import java.time.ZonedDateTime;
import java.util.List;

public class ProductLikeV1Dto {

    public record LikeResponse(
        Long productId,
        ZonedDateTime createdAt
    ) {
        public static LikeResponse from(ProductLikeResult result) {
            return new LikeResponse(result.productId(), result.createdAt());
        }
    }

    public record ListResponse(
        List<ListItem> items
    ) {
        public record ListItem(
            Long productId,
            ZonedDateTime createdAt
        ) {
            public static ListItem from(ProductLikeResult result) {
                return new ListItem(result.productId(), result.createdAt());
            }
        }
    }
}
