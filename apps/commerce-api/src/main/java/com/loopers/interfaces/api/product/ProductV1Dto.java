package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductListItemInfo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 고객용 상품 API DTO.
 * Application의 ProductDetailInfo·ProductListItemInfo와 매핑하며, interfaces 전용으로 분리한다.
 */
public class ProductV1Dto {

    /** 목록 한 건 */
    public record ListItemResponse(
        Long id,
        String name,
        BigDecimal price,
        Long brandId,
        String brandName,
        long likeCount
    ) {
        public static ListItemResponse from(ProductListItemInfo info) {
            if (info == null) return null;
            return new ListItemResponse(
                info.id(),
                info.name(),
                info.price(),
                info.brandId(),
                info.brandName(),
                info.likeCount()
            );
        }
    }

    /** 목록 응답 (페이징 포함) */
    public record ListResponse(
        List<ListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static ListResponse from(org.springframework.data.domain.Page<ProductListItemInfo> page) {
            if (page == null) return new ListResponse(List.of(), 0, 0, 0L, 0);
            List<ListItemResponse> content = page.getContent().stream()
                .map(ListItemResponse::from)
                .toList();
            return new ListResponse(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }

    public record DetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        int stockQuantity,
        long likeCount
    ) {
        public static DetailResponse from(ProductDetailInfo info) {
            if (info == null) {
                return null;
            }
            return new DetailResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.stockQuantity(),
                info.likeCount()
            );
        }
    }
}
