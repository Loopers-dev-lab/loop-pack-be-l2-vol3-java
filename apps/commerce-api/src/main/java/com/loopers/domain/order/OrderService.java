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
    private final OrderKeyGenerator orderKeyGenerator;

    /**
     * 새로운 주문을 생성한다.
     *
     * <p>고유한 주문 키를 생성하여 주문에 할당한다.</p>
     *
     * @param cart           장바구니
     * @param discountAmount 쿠폰 할인 금액
     * @param ownedCouponId  적용된 보유 쿠폰 ID (없으면 null)
     * @return 생성된 주문
     */
    @Transactional
    public Order create(Cart cart, Money discountAmount, Long ownedCouponId) {
        String orderKey = orderKeyGenerator.generate();
        Order order = Order.create(orderKey, cart, discountAmount, ownedCouponId);
        return orderRepository.save(order);
    }

    /**
     * 주문 키로 사용자의 주문을 조회한다.
     *
     * @param userId   사용자 ID
     * @param orderKey 주문 키
     * @return 주문 항목을 포함한 주문
     * @throws CoreException 주문이 존재하지 않거나 본인의 주문이 아닌 경우
     */
    public Order getMyOrder(Long userId, String orderKey) {
        Order order = orderRepository.findByOrderKeyWithItems(orderKey)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        order.validateOwner(userId);
        return order;
    }
}
