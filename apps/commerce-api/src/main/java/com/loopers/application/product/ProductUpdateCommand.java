package com.loopers.application.product;

import java.math.BigDecimal;

/**
 * 상품 수정 커맨드.
 * <p>
 * interfaces → application 경계에서 사용되는 상품 수정 요청 객체이다.
 * </p>
 *
 * @param productId   수정할 상품 ID
 * @param productName 변경할 상품명
 * @param price       변경할 가격
 * @param description 변경할 상품 설명
 * @param imageUrl    변경할 이미지 URL
 */
public record ProductUpdateCommand(String productId, String productName,
                                    BigDecimal price, String description,
                                    String imageUrl) {
}
