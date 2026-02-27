package com.loopers.support.enums;

/**
 * 장바구니 항목이 주문 불가능한 사유. DB 컬럼이 아닌 서비스 계산값.
 */
public enum UnavailableReason {
    /** 상품이 소프트 삭제됨 */
    DELETED,
    /** 상품 display_status가 HIDDEN */
    HIDDEN,
    /** 소속 브랜드가 소프트 삭제됨 */
    BRAND_DELETED,
    /** 소속 브랜드 display_status가 HIDDEN */
    BRAND_HIDDEN,
    /** 상품 판매 중지 상태 */
    STOPPED,
    /** 상품 일시 품절 상태 */
    TEMP_SOLD_OUT,
    /** 요청 수량보다 가용 재고가 부족 */
    OUT_OF_STOCK,
    /** 요청 수량이 0 이하 */
    INVALID_QUANTITY;

    /**
     * 상품/브랜드/재고 상태를 기반으로 주문 불가 사유를 평가한다.
     * 주문 가능하면 null을 반환한다.
     *
     * <p>검사 우선순위: 상품 삭제 → 브랜드 삭제 → 상품 비노출 → 브랜드 비노출
     * → 판매 중지 → 일시 품절 → 수량 유효성 → 재고 부족</p>
     *
     * @param productDeleted       상품 소프트 삭제 여부
     * @param productDisplayStatus 상품 노출 상태
     * @param saleStatus           상품 판매 상태
     * @param brandDeleted         브랜드 소프트 삭제 여부
     * @param brandDisplayStatus   브랜드 노출 상태
     * @param availableQty         가용 재고 수량
     * @param requestedQty         요청 수량
     * @return 주문 불가 사유 (주문 가능 시 null)
     */
    public static UnavailableReason evaluate(
            boolean productDeleted, DisplayStatus productDisplayStatus, ProductSaleStatus saleStatus,
            boolean brandDeleted, DisplayStatus brandDisplayStatus,
            int availableQty, int requestedQty) {
        if (productDeleted) return DELETED;
        if (brandDeleted) return BRAND_DELETED;
        if (productDisplayStatus == DisplayStatus.HIDDEN) return HIDDEN;
        if (brandDisplayStatus == DisplayStatus.HIDDEN) return BRAND_HIDDEN;
        if (saleStatus == ProductSaleStatus.STOPPED) return STOPPED;
        if (saleStatus == ProductSaleStatus.TEMP_SOLD_OUT) return TEMP_SOLD_OUT;
        if (requestedQty <= 0) return INVALID_QUANTITY;
        if (availableQty < requestedQty) return OUT_OF_STOCK;
        return null;
    }
}
