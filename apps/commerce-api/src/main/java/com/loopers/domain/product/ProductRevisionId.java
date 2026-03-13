package com.loopers.domain.product;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 상품 변경 이력 복합 기본키 클래스.
 * <p>
 * {@code productId}와 {@code revisionSeq}의 조합으로 구성되며,
 * 상품별 변경 이력의 순번을 기반으로 고유하게 식별한다.
 * JPA {@link jakarta.persistence.IdClass} 전략에서 사용된다.
 * </p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProductRevisionId implements Serializable {
    private Long productId;
    private Long revisionSeq;
}
