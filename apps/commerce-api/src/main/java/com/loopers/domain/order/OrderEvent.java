package com.loopers.domain.order;

/**
 * 주문 도메인에서 발생하는 이벤트.
 *
 * <p>Order 엔티티에서 {@code registerEvent()}로 등록되며,
 * {@code repository.save()} 시점에 발행된다.</p>
 */
public class OrderEvent {

    /**
     * 주문이 실패 처리되었을 때 발행되는 이벤트.
     *
     * <p>리스너에서 재고 복원, 쿠폰 복원 등 보상 처리를 수행한다.</p>
     *
     * @param orderId 실패한 주문 ID
     */
    public record OrderFailed(Long orderId) {
    }
}
