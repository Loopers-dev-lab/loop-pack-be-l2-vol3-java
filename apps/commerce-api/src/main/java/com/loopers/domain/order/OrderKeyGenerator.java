package com.loopers.domain.order;

/**
 * 주문 키(orderKey) 생성 포트.
 *
 * <p>외부에서 주문을 식별하기 위한 고유 키를 생성한다.
 * 생성 전략은 인프라스트럭처 계층의 구현체가 결정한다.</p>
 */
public interface OrderKeyGenerator {

    /**
     * 고유한 주문 키를 생성한다.
     *
     * @return URL-safe한 고유 문자열
     */
    String generate();
}
