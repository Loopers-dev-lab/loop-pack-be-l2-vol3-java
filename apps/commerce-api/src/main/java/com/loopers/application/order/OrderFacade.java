package com.loopers.application.order;

import com.loopers.application.coupon.CouponIssueService;
import com.loopers.application.coupon.CouponService;
import com.loopers.domain.coupon.CouponIssueModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;
    private final CouponIssueService couponIssueService;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResult placeOrder(Long userId, List<OrderItemCommand> commands, Long couponIssueId) {
        // 1. 락 없이: 상품 조회 + 금액 계산 (productId 오름차순 정렬 — 데드락 방지)
        List<OrderItemCommand> sorted = commands.stream()
            .sorted(Comparator.comparing(OrderItemCommand::productId))
            .toList();

        Money totalAmount = Money.ZERO;
        List<SnapshotHolder> snapshots = new ArrayList<>();

        for (OrderItemCommand cmd : sorted) {
            ProductModel product = productService.getById(cmd.productId());
            Money subtotal = product.getPrice().multiply(cmd.quantity());
            totalAmount = totalAmount.add(subtotal);
            snapshots.add(new SnapshotHolder(
                product.getId(), product.getName(), product.getPrice(), cmd.quantity()
            ));
        }

        // 2. 비관적 락: 재고 차감 (productId 오름차순 — 데드락 방지)
        for (OrderItemCommand cmd : sorted) {
            StockModel stock = stockService.getByProductIdForUpdate(cmd.productId());
            stock.decrease(cmd.quantity());
        }

        // 3. 비관적 락: 쿠폰 검증 + 할인 + 사용 처리 (한 번만 FOR UPDATE로 조회)
        Money discountAmount = Money.ZERO;
        CouponIssueModel couponIssue = null;
        if (couponIssueId != null) {
            couponIssue = couponIssueService.getCouponIssueForUpdate(couponIssueId);
            couponIssue.validateOwner(userId);
            CouponModel coupon = couponService.getCoupon(couponIssue.couponId());
            coupon.validateUsable(totalAmount);
            discountAmount = coupon.calculateDiscount(totalAmount);
            couponIssue.use(null);
        }

        // 4. 주문 생성
        OrderModel order = orderService.save(new OrderModel(userId, totalAmount, discountAmount, couponIssueId));

        // 5. 쿠폰에 orderId 연결
        if (couponIssue != null) {
            couponIssue.setOrderId(order.getId());
        }

        List<OrderItemModel> items = snapshots.stream()
            .map(s -> new OrderItemModel(
                order.getId(), s.productId(), s.productName(), s.productPrice(), s.quantity()
            ))
            .toList();

        List<OrderItemModel> savedItems = orderService.saveAllItems(items);

        eventPublisher.publishEvent(new OrderPlacedEvent(
            order.getId(), userId, (long) totalAmount.value()
        ));

        return OrderResult.of(order, savedItems);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long orderId, Long userId) {
        OrderModel order = orderService.getOrder(orderId, userId);
        List<OrderItemModel> items = orderService.getOrderItems(orderId);
        return OrderInfo.from(order, items);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrdersByUser(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        List<OrderModel> orders = orderService.getOrdersByUser(userId, startAt, endAt);
        return orders.stream().map(OrderInfo::summaryFrom).toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderForAdmin(Long orderId) {
        OrderModel order = orderService.getOrderForAdmin(orderId);
        List<OrderItemModel> items = orderService.getOrderItems(orderId);
        return OrderInfo.from(order, items);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllForAdmin(Pageable pageable) {
        Page<OrderModel> orders = orderService.getAllForAdmin(pageable);
        return orders.map(OrderInfo::summaryFrom);
    }

    private record SnapshotHolder(
        Long productId, String productName, Money productPrice, int quantity
    ) {}
}
