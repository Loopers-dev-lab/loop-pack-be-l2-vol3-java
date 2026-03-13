package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartInfo;
import com.loopers.support.enums.UnavailableReason;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 장바구니 API V1 요청/응답 DTO 모음.
 *
 * <p>장바구니 관련 REST API의 HTTP 요청 및 응답 데이터 구조를 정의한다.</p>
 */
public class CartV1Dto {

    /**
     * 장바구니 상품 추가 요청 DTO.
     *
     * <p>추가할 상품 ID와 수량을 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddItemRequest {
        private Long productId;
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        private int quantity;
    }

    /**
     * 장바구니 수량 변경 요청 DTO.
     *
     * <p>변경할 새 수량을 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChangeQtyRequest {
        @Min(value = 1, message = "수량은 1 이상이어야 합니다")
        private int quantity;
    }

    /**
     * 장바구니 항목 응답 DTO.
     *
     * <p>상품 정보, 수량, 주문 가능 여부 및 불가 사유를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class CartItemResponse {
        private Long productId;
        private String productName;
        private BigDecimal price;
        private String brandName;
        private int quantity;
        private boolean available;
        private UnavailableReason unavailableReason;
        private int availableStock;

        /**
         * {@link CartInfo}를 장바구니 항목 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param info 변환할 장바구니 도메인 Info 객체
         * @return 변환된 CartItemResponse
         */
        public static CartItemResponse from(CartInfo info) {
            return CartItemResponse.builder()
                    .productId(info.getProductId())
                    .productName(info.getProductName())
                    .price(info.getPrice())
                    .brandName(info.getBrandName())
                    .quantity(info.getQuantity())
                    .available(info.isAvailable())
                    .unavailableReason(info.getUnavailableReason())
                    .availableStock(info.getAvailableStock())
                    .build();
        }
    }
}
