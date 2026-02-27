package com.loopers.interfaces.apiadmin;

import com.loopers.application.cart.CartInfo;
import com.loopers.support.enums.UnavailableReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 관리자 장바구니 API의 요청/응답 DTO를 정의하는 클래스.
 */
public class AdminCartV1Dto {

    /**
     * 관리자용 장바구니 항목 응답 DTO.
     *
     * <p>주문 가능 여부, 불가 사유, 가용 재고 등 관리자에게 필요한 상세 정보를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class AdminCartItemResponse {
        private String userId;
        private String productId;
        private int quantity;
        private boolean available;
        private UnavailableReason unavailableReason;
        private String productName;
        private BigDecimal price;
        private String brandName;
        private int availableStock;

        /**
         * {@link CartInfo}를 관리자 장바구니 항목 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param info 장바구니 정보 DTO
         * @return 변환된 관리자 장바구니 항목 응답 DTO
         */
        public static AdminCartItemResponse from(CartInfo info) {
            return AdminCartItemResponse.builder()
                    .userId(info.getUserId())
                    .productId(info.getProductId())
                    .quantity(info.getQuantity())
                    .available(info.isAvailable())
                    .unavailableReason(info.getUnavailableReason())
                    .productName(info.getProductName())
                    .price(info.getPrice())
                    .brandName(info.getBrandName())
                    .availableStock(info.getAvailableStock())
                    .build();
        }
    }
}
