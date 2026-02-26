package com.loopers.application.order;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 주문 상세를 조회합니다.
 *
 * <p>주문 항목과 주문자 정보를 함께 조회하며, 개인정보 보호를 위해 주문자 이름이 마스킹 처리되어 반환됩니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadOrderDetailUseCase {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * @param orderId 주문 ID
     * @return 어드민용 주문 상세 정보 (주문자명 마스킹 포함)
     */
    @Transactional(readOnly = true)
    public AdminOrderDetailResult execute(Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        User user = userRepository.findById(order.getUserId())
                .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
        return AdminOrderDetailResult.from(order, user.getName().masked());
    }
}
