package com.loopers.application.product;

import java.math.BigDecimal;

/**
 * 상품 생성 커맨드.
 * <p>
 * interfaces → application 경계에서 사용되는 상품 생성 요청 객체이다.
 * </p>
 *
 * @param productName  상품명
 * @param brandId      브랜드 ID
 * @param price        가격
 * @param description  상품 설명
 * @param initialStock 초기 재고 수량
 */
public record ProductCreateCommand(String productName, String brandId,
                                    BigDecimal price, String description,
                                    int initialStock) {
}
