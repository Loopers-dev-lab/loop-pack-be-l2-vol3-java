package com.loopers.domain.order;

import org.springframework.transaction.annotation.Transactional;

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
     * 장바구니 정보를 기반으로 주문을 생성한다.
     *
     * @param cart 장바구니
     * @return 생성된 주문
     */
    @Transactional
    public Order create(Cart cart) {
        Order order = Order.create(cart);
        return orderRepository.save(order);
    }

    /**
     * 사용자 본인의 주문을 상세 조회한다.
     *
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @return 주문 (주문 항목 포함)
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
