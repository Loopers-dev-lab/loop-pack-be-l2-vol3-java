package com.loopers.application.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponApplyResult;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.event.EventOutbox;
import com.loopers.domain.event.EventOutboxRepository;
import com.loopers.domain.event.OrderCancelledEvent;
import com.loopers.domain.event.OrderCreatedEvent;
import com.loopers.domain.event.OrderItemSnapshot;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CouponFacade couponFacade;
    private final EventOutboxRepository eventOutboxRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public Order createOrder(Long memberId, List<OrderItemRequest> itemRequests) {
        return createOrder(memberId, itemRequests, null);
    }

    @Transactional
    public Order createOrder(Long memberId, List<OrderItemRequest> itemRequests, Long couponIssueId) {
        // 1. 상품 조회 — 비관적 락 + ID 오름차순 (데드락 방지)
        List<Long> sortedProductIds = itemRequests.stream()
            .map(OrderItemRequest::productId)
            .distinct()
            .sorted()
            .toList();

        Map<Long, Product> productMap = productRepository.findAllByIdsWithLock(sortedProductIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (OrderItemRequest req : itemRequests) {
            if (productMap.get(req.productId()) == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
            }
        }

        // 2. 브랜드 한 번에 조회 (N+1 방지)
        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandRepository.findAllByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));

        // 3. 스냅샷 생성
        List<Order.ItemSnapshot> snapshots = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            Product product = productMap.get(req.productId());
            Brand brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : null;

            snapshots.add(new Order.ItemSnapshot(
                product.getId(),
                product.getName(),
                product.getPrice().getValue(),
                brandName,
                req.quantity()
            ));
        }

        // 4. 재고 차감 — 도메인 엔티티에 위임 (비관적 락으로 보호)
        for (OrderItemRequest req : itemRequests) {
            Product product = productMap.get(req.productId());
            product.decreaseStock(req.quantity());
        }

        // 5. 쿠폰 적용
        Long resolvedCouponIssueId = null;
        int discountAmount = 0;

        if (couponIssueId != null) {
            int originalTotalPrice = snapshots.stream()
                .mapToInt(s -> s.productPrice() * s.quantity())
                .sum();

            CouponApplyResult result = couponFacade.applyCouponToOrder(
                couponIssueId, memberId, originalTotalPrice);
            resolvedCouponIssueId = result.couponIssueId();
            discountAmount = result.discountAmount();
        }

        // 6. 주문 저장
        Order order = orderRepository.save(
            Order.create(memberId, snapshots, resolvedCouponIssueId, discountAmount));

        // 7. 쿠폰에 주문 ID 연결
        if (resolvedCouponIssueId != null) {
            couponFacade.linkCouponToOrder(resolvedCouponIssueId, order.getId());
        }

        // 8. Outbox INSERT + 이벤트 발행
        List<OrderItemSnapshot> eventItems = order.getItems().stream()
            .map(item -> new OrderItemSnapshot(item.getProductId(), item.getQuantity(), item.getProductPrice()))
            .toList();

        EventOutbox outbox = EventOutbox.create("order", String.valueOf(order.getId()),
            "ORDER_CREATED", buildOrderPayload(order.getId(), memberId, eventItems));
        eventOutboxRepository.save(outbox);

        applicationEventPublisher.publishEvent(new OrderCreatedEvent(order.getId(), memberId, eventItems));

        return order;
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Order getOrder(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
        }
        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 취소할 수 있습니다.");
        }
        order.cancel();

        // 재고 복원 — 비관적 락 + 도메인 엔티티 위임
        List<Long> productIds = order.getItems().stream()
            .map(OrderItem::getProductId)
            .distinct()
            .sorted()
            .toList();
        Map<Long, Product> productMap = productRepository.findAllByIdsWithLock(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (OrderItem item : order.getItems()) {
            Product product = productMap.get(item.getProductId());
            product.increaseStock(item.getQuantity());
        }

        // 쿠폰 복원
        if (order.getCouponIssueId() != null) {
            couponFacade.restoreCoupon(order.getCouponIssueId());
        }

        // Outbox INSERT + 이벤트 발행
        List<OrderItemSnapshot> eventItems = order.getItems().stream()
            .map(item -> new OrderItemSnapshot(item.getProductId(), item.getQuantity(), item.getProductPrice()))
            .toList();

        EventOutbox outbox = EventOutbox.create("order", String.valueOf(orderId),
            "ORDER_CANCELLED", buildOrderPayload(orderId, memberId, eventItems));
        eventOutboxRepository.save(outbox);

        applicationEventPublisher.publishEvent(new OrderCancelledEvent(orderId, memberId, eventItems));
    }

    public List<Order> getOrdersByMemberId(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt) {
        if (startAt != null && endAt != null) {
            return orderRepository.findAllByMemberIdAndCreatedAtBetween(memberId, startAt, endAt);
        }
        return orderRepository.findAllByMemberId(memberId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public record OrderItemRequest(Long productId, int quantity) {}

    private String buildOrderPayload(Long orderId, Long memberId, List<OrderItemSnapshot> items) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "memberId", memberId,
                "items", items
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("주문 이벤트 페이로드 직렬화 실패", e);
        }
    }
}
