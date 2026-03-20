package com.loopers.application.cart;

import com.loopers.support.enums.UnavailableReason;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 장바구니 항목 정보 DTO.
 * <p>
 * 장바구니 항목, 상품, 브랜드, 재고 정보를 조합하여
 * 주문 가능 여부와 불가 사유를 포함한 응답 객체이다.
 * 도메인 모델을 직접 노출하지 않고 interfaces 계층에 전달하기 위한 객체이다.
 * </p>
 * <p>
 * 생성은 {@link CartFacade}에서 빌더로 수행한다.
 * </p>
 */
@Getter
@Builder
public class CartInfo {
    private final Long userId;
    private final Long productId;
    private final int quantity;
    private final boolean available;
    private final UnavailableReason unavailableReason;
    private final String productName;
    private final BigDecimal price;
    private final Long brandId;
    private final String brandName;
    private final String imageUrl;
    private final int availableStock;
}
