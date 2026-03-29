package com.loopers.domain.product;

import java.util.UUID;

/**
 * 상품 도메인에서 발생하는 이벤트.
 *
 * <p>Product 엔티티에서 {@code registerEvent()}로 등록되며,
 * {@code repository.save()} 시점에 발행된다.</p>
 */
public class ProductEvent {

    /**
     * 상품이 삭제되었을 때 발행되는 이벤트.
     *
     * @param eventId 이벤트 식별자
     * @param productId 삭제된 상품 ID
     */
    public record ProductDeleted(UUID eventId, Long productId) {

        public static ProductDeleted from(Product product) {
            return new ProductDeleted(UUID.randomUUID(), product.getId());
        }
    }

    /**
     * 상품이 조회되었을 때 발행되는 이벤트.
     *
     * <p>조회 API는 쓰기 트랜잭션이 없으므로 {@code AbstractAggregateRoot}가 아닌
     * {@code ProductEventPublisher}를 통해 직접 발행된다.
     * {@code @EventListener} + {@code @Transactional}로 Outbox에 저장되며,
     * 외부 발행은 Relay 스케줄러에 위임한다.</p>
     *
     * @param eventId   이벤트 식별자
     * @param productId 조회된 상품 ID
     */
    public record ProductViewed(UUID eventId, Long productId) {

        public static ProductViewed from(Product product) {
            return new ProductViewed(UUID.randomUUID(), product.getId());
        }
    }
}
