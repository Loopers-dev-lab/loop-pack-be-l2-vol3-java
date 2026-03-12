package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 주문 Application Service.
 * 여러 BC 조합 및 트랜잭션 경계 담당.
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    /**
     * 주문 생성.
     * 1. 상품 조회 (비관적 락)
     * 2. 재고 차감
     * 3. 주문 생성
     *
     * @param userId 사용자 ID
     * @param items 주문 항목 요청 목록
     * @return 생성된 주문 결과
     */
    @Transactional
    public OrderResult placeOrder(Long userId, List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemRequest req : items) {
            // 1) 비관적 락으로 상품 조회
            Product product = productRepository.findByIdWithLock(req.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            // 2) 재고 차감 (도메인 규칙)
            product.decreaseStock(req.quantity());
            productRepository.save(product);

            // 3) OrderItem 생성 (가격 스냅샷)
            orderItems.add(OrderItem.create(
                product.getId(),
                product.getName(),
                req.quantity(),
                product.getPrice()
            ));
        }

        // 4) Order 생성/저장
        Order order = Order.create(userId, orderItems);
        Order saved = orderRepository.save(order);

        return OrderResult.from(saved);
    }

    /**
     * 주문 조회.
     *
     * @param orderId 주문 ID
     * @param userId 사용자 ID
     * @return 주문 결과
     */
    @Transactional(readOnly = true)
    public OrderResult getOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        return OrderResult.from(order);
    }

    /**
     * 사용자의 주문 목록 조회.
     *
     * @param userId 사용자 ID
     * @param offset 시작 위치
     * @param limit 조회 개수
     * @return 주문 결과 목록
     */
    @Transactional(readOnly = true)
    public List<OrderResult> getOrders(Long userId, int offset, int limit) {
        return orderRepository.findAllByUserId(userId, offset, limit).stream()
            .map(OrderResult::from)
            .toList();
    }

    /**
     * 사용자의 주문 수 조회.
     *
     * @param userId 사용자 ID
     * @return 주문 수
     */
    @Transactional(readOnly = true)
    public long countOrders(Long userId) {
        return orderRepository.countByUserId(userId);
    }
}
