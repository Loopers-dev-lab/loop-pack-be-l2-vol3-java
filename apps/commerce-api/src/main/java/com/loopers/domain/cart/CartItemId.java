package com.loopers.domain.cart;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 장바구니 항목 복합 기본키 클래스.
 * <p>
 * {@code userId}와 {@code productId}의 조합으로 구성되며,
 * 사용자당 동일 상품은 1개의 장바구니 항목만 존재하도록 보장한다.
 * JPA {@link jakarta.persistence.IdClass} 전략에서 사용된다.
 * </p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CartItemId implements Serializable {
    private Long userId;
    private Long productId;
}
