package com.loopers.domain.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.common.Quantity;
import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final StockDeductionService stockDeductionService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public Order createOrder(
        Long memberId,
        List<Product> products,
        Map<Long, Brand> brandMap,
        Map<Long, Quantity> deductionMap
    ) {
        return createOrder(memberId, products, brandMap, deductionMap, null, Optional.empty());
    }

    @Transactional
    public Order createOrder(
        Long memberId,
        List<Product> products,
        Map<Long, Brand> brandMap,
        Map<Long, Quantity> deductionMap,
        Long userCouponId,
        Optional<CouponDiscount> couponDiscount
    ) {
        stockDeductionService.deductAll(deductionMap);

        long totalAmount = products.stream()
            .mapToLong(p -> p.getPrice() * deductionMap.get(p.getId()).getValue())
            .sum();

        long discountAmount = couponDiscount.map(CouponDiscount::discountAmount).orElse(0L);

        Order order = orderRepository.save(
            new Order(memberId, new Money(totalAmount), OrderStatus.CREATED, userCouponId, discountAmount)
        );

        List<OrderItem> orderItems = products.stream()
            .map(product -> {
                Brand brand = brandMap.get(product.getBrandId());
                Quantity quantity = deductionMap.get(product.getId());
                ProductSnapshot snapshot = new ProductSnapshot(
                    product.getName(),
                    new Money(product.getPrice()),
                    brand.getName()
                );
                return new OrderItem(order.getId(), product.getId(), OrderItemStatus.ORDERED, snapshot, quantity);
            })
            .toList();

        orderItemRepository.saveAll(orderItems);

        return order;
    }

    // 특정 회원의 주문 목록을 최신순으로 페이징 조회한다.
    @Transactional(readOnly = true)
    public Page<Order> getOrderList(Long memberId, Pageable pageable) {
        return orderRepository.findAllByMemberId(memberId, pageable);
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
        order.confirm();
        orderRepository.save(order);
    }

    /**
     * 주문 단건 조회 + 본인 소유 검증.
     * 타인의 주문이거나 존재하지 않으면 보안상 동일하게 NOT_FOUND를 반환한다.
     */
    @Transactional(readOnly = true)
    public Order getOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));
        if (!order.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다.");
        }
        return order;
    }
}
