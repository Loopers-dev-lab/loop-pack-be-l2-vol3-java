package com.loopers.interfaces.api.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.inventory.InventoryInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductStatus;

import java.time.ZonedDateTime;
import java.util.List;

/** 상품 어드민 API 응답 DTO */
public class AdminProductResponse {

    /** 상품 상세 정보 (Admin용 - 상태, 재고, 생성/수정 시각 포함) */
    public record ProductDetail(
            Long id,
            Long brandId,
            String brandName,
            String name,
            String description,
            int basePrice,
            ProductStatus status,
            int likeCount,
            int quantity,
            int reservedQty,
            int availableQuantity,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static ProductDetail from(ProductInfo product, BrandInfo brand, InventoryInfo inventory) {
            return new ProductDetail(
                    product.id(), product.brandId(), brand.name(),
                    product.name(), product.description(), product.basePrice(),
                    product.status(), product.likeCount(),
                    inventory.quantity(), inventory.reservedQty(), inventory.availableQuantity(),
                    product.createdAt(), product.updatedAt()
            );
        }

        public static ProductDetail from(ProductInfo product) {
            return new ProductDetail(
                    product.id(), product.brandId(), null,
                    product.name(), product.description(), product.basePrice(),
                    product.status(), product.likeCount(),
                    0, 0, 0,
                    product.createdAt(), product.updatedAt()
            );
        }
    }

    /** 상품 목록 + 페이지네이션 메타 정보 */
    public record ProductListResponse(
            List<ProductDetail> products,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
