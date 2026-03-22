package com.loopers.domain.product;

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
     * @param productId 삭제된 상품 ID
     */
    public record ProductDeleted(Long productId) {
    }
}
