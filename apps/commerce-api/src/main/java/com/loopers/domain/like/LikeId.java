package com.loopers.domain.like;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 좋아요 복합 기본키 클래스.
 * userId와 productId의 조합으로 사용자당 상품별 좋아요 고유성을 보장한다.
 * JPA {@code @IdClass} 전략에 사용된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LikeId implements Serializable {
    private Long userId;
    private Long productId;
}
