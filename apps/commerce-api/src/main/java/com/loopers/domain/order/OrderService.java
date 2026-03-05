package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;

    // 주문 생성 (US-O01)
    @Transactional
    public Order create(Long userId, List<OrderItem> items, Long userCouponId, Money originalAmount, Money discountAmount) {
        Order order = new Order(userId, items, userCouponId, originalAmount, discountAmount);
        return orderRepository.save(order);
    }

    // 주문 단건 조회
    @Transactional(readOnly = true)
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
    }

    // 회원 주문 목록 조회 (BR-O06: 자신의 주문만, BR-O08: 기간 필터)
    @Transactional(readOnly = true)
    public List<Order> findAllByUserId(Long userId, ZonedDateTime from, ZonedDateTime to) {
        return orderRepository.findAllByUserIdAndCreatedAtBetween(userId, from, to);
    }

    // 전체 주문 목록 조회 (관리자, BR-O07)
    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
