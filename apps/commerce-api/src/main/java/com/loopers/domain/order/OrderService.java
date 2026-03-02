package com.loopers.domain.order;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 주문 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * 새로운 주문을 생성한다.
     *
     * @param cart           장바구니
     * @param discountAmount 쿠폰 할인 금액
     * @param ownedCouponId  적용된 보유 쿠폰 ID (없으면 null)
     * @return 생성된 주문
     */
    @Transactional
    public Order create(Cart cart, Money discountAmount, Long ownedCouponId) {
        Order order = Order.create(cart, discountAmount, ownedCouponId);
        return orderRepository.save(order);
    }

    /**
     * 사용자의 주문을 조회한다.
     *
     * @param userId  사용자 ID
     * @param orderId 주문 ID
     * @return 주문 항목을 포함한 주문
     * @throws CoreException 주문이 존재하지 않거나 본인의 주문이 아닌 경우
     */
    @Transactional(readOnly = true)
    public Order getMyOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        order.validateOwner(userId);
        return order;
    }
}
