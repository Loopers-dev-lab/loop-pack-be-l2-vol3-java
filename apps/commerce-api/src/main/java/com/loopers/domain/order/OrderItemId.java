package com.loopers.domain.order;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 주문 항목 복합 기본키 클래스.
 * <p>
 * {@code orderId}와 {@code orderItemSeq}의 조합으로 구성되며,
 * 주문 내 항목의 순번을 기반으로 고유하게 식별한다.
 * JPA {@link jakarta.persistence.IdClass} 전략에서 사용된다.
 * </p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OrderItemId implements Serializable {
    private Long orderId;
    private int orderItemSeq;
}
