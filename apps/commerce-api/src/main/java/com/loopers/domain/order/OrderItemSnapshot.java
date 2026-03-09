package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;

/**
 * 주문 항목 스냅샷. 주문 시점의 상품/브랜드 정보를 보존한다.
 * <p>
 * application 레이어에서 ProductModel + BrandModel 조합으로 생성되며,
 * 도메인 서비스(OrderService)에 전달하여 주문 항목을 저장하는 데 사용한다.
 * </p>
 *
 * @param productId   상품 ID
 * @param quantity    주문 수량
 * @param productName 상품명
 * @param unitPrice   단가
 * @param brandId     브랜드 ID
 * @param brandName   브랜드명
 * @param imageUrl    이미지 URL
 */
public record OrderItemSnapshot(String productId, int quantity, String productName,
                                 BigDecimal unitPrice, String brandId,
                                 String brandName, String imageUrl) {

    /**
     * ProductModel과 BrandModel로부터 주문 항목 스냅샷을 생성한다.
     *
     * @param product  상품 도메인 모델
     * @param brand    브랜드 도메인 모델
     * @param quantity 주문 수량
     * @return 주문 항목 스냅샷
     */
    public static OrderItemSnapshot from(ProductModel product, BrandModel brand, int quantity) {
        return new OrderItemSnapshot(
                product.getProductId(), quantity,
                product.getProductName(), product.getPrice(),
                brand.getBrandId(), brand.getBrandName(), product.getImageUrl());
    }

    /**
     * 항목별 주문 금액(단가 x 수량)을 계산한다.
     *
     * @return 항목 금액
     */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
