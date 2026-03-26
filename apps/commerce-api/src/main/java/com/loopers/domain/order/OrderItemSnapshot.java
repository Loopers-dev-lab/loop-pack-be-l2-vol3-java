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
 * @param productId      상품 ID
 * @param quantity       주문 수량
 * @param productName    상품명
 * @param unitPrice      단가
 * @param brandId        브랜드 ID
 * @param brandName      브랜드명
 * @param imageUrl       이미지 URL
 * @param originalAmount 할인 전 금액 (unitPrice * quantity)
 * @param discountAmount 이 항목에 비례 배분된 할인 금액
 * @param finalAmount    최종 금액 (originalAmount - discountAmount)
 */
public record OrderItemSnapshot(Long productId, int quantity, String productName,
                                 BigDecimal unitPrice, String brandId,
                                 String brandName, String imageUrl,
                                 BigDecimal originalAmount,
                                 BigDecimal discountAmount,
                                 BigDecimal finalAmount) {

    /**
     * 할인 없이 ProductModel과 BrandModel로부터 주문 항목 스냅샷을 생성한다.
     */
    public static OrderItemSnapshot from(ProductModel product, BrandModel brand, int quantity) {
        BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        return new OrderItemSnapshot(
                product.getProductId(), quantity,
                product.getProductName(), product.getPrice(),
                String.valueOf(brand.getBrandId()), brand.getBrandName(), product.getImageUrl(),
                lineTotal, BigDecimal.ZERO, lineTotal);
    }

    /**
     * 할인 금액을 적용하여 주문 항목 스냅샷을 생성한다.
     *
     * @param product        상품 도메인 모델
     * @param brand          브랜드 도메인 모델
     * @param quantity       주문 수량
     * @param discountAmount 이 항목에 배분된 할인 금액
     * @return 할인이 적용된 주문 항목 스냅샷
     */
    public static OrderItemSnapshot from(ProductModel product, BrandModel brand, int quantity,
                                          BigDecimal discountAmount) {
        BigDecimal originalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        BigDecimal finalAmount = originalAmount.subtract(discountAmount);
        return new OrderItemSnapshot(
                product.getProductId(), quantity,
                product.getProductName(), product.getPrice(),
                String.valueOf(brand.getBrandId()), brand.getBrandName(), product.getImageUrl(),
                originalAmount, discountAmount, finalAmount);
    }

    /**
     * 할인 금액을 적용한 새 스냅샷을 반환한다.
     * 기존 스냅샷의 상품/브랜드 정보는 그대로 유지하고 할인 관련 필드만 변경한다.
     *
     * @param discountAmount 이 항목에 배분된 할인 금액
     * @return 할인이 적용된 새 스냅샷
     */
    public OrderItemSnapshot withDiscount(BigDecimal discountAmount) {
        return new OrderItemSnapshot(
                productId, quantity, productName, unitPrice,
                brandId, brandName, imageUrl,
                originalAmount, discountAmount,
                originalAmount.subtract(discountAmount));
    }

    /**
     * 항목별 최종 주문 금액을 반환한다.
     *
     * @return finalAmount
     */
    public BigDecimal lineTotal() {
        return finalAmount;
    }
}
