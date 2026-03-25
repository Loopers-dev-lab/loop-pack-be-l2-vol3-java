package com.loopers.domain.product;

/**
 * 상품 도메인 이벤트 발행 Port.
 *
 * <p>쓰기 트랜잭션이 없는 조회 API에서 이벤트를 발행하기 위해 사용한다.
 * {@code AbstractAggregateRoot.registerEvent()}는 {@code repository.save()} 시점에
 * 발행되므로, 조회 시에는 이 Port를 통해 직접 발행한다.</p>
 */
public interface ProductEventPublisher {

    /**
     * 상품 도메인 이벤트를 발행한다.
     *
     * @param event 발행할 이벤트
     */
    void publishEvent(Object event);
}
