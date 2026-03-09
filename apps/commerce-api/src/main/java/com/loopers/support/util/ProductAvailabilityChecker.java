package com.loopers.support.util;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.enums.ProductSaleStatus;
import com.loopers.support.enums.UnavailableReason;

/**
 * 상품 주문 가능 여부를 판정하는 유틸리티 클래스.
 * 상품/브랜드의 삭제, 노출, 판매 상태 및 재고를 종합적으로 검사한다.
 */
public class ProductAvailabilityChecker {

    private ProductAvailabilityChecker() {
    }

    /**
     * 상품의 주문 가능 여부를 판정하고, 불가능하면 사유를 반환한다.
     *
     * @return null이면 주문 가능, non-null이면 주문 불가 사유
     */
    public static UnavailableReason check(ProductModel product, BrandModel brand,
                                           ProductStockModel stock, int requestedQty) {
        if (product.isDeleted()) return UnavailableReason.DELETED;
        if (brand.isDeleted()) return UnavailableReason.BRAND_DELETED;
        if (product.getDisplayStatus() == DisplayStatus.HIDDEN) return UnavailableReason.HIDDEN;
        if (brand.getDisplayStatus() == DisplayStatus.HIDDEN) return UnavailableReason.BRAND_HIDDEN;
        if (product.getSaleStatus() == ProductSaleStatus.STOPPED) return UnavailableReason.STOPPED;
        if (product.getSaleStatus() == ProductSaleStatus.TEMP_SOLD_OUT) return UnavailableReason.TEMP_SOLD_OUT;
        if (stock != null && stock.getAvailableQty() < requestedQty) return UnavailableReason.OUT_OF_STOCK;
        return null;
    }
}
