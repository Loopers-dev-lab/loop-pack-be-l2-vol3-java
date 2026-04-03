package com.loopers.application.order;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.order.OrderEventPublisher;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDetailCacheEvictEvent;
import com.loopers.domain.product.ProductEventPublisher;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.useraction.UserActionEvent;
import com.loopers.domain.useraction.UserActionEventPublisher;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final OrderService orderService;
    private final ProductService productService;
    private final BrandService brandService;
    private final CouponService couponService;
    private final ProductEventPublisher productEventPublisher;
    private final OrderEventPublisher orderEventPublisher;
    private final UserActionEventPublisher userActionEventPublisher;

    /**
     * 주문 생성 (US-O01)
     *
     * 작업 순서:
     * ① 재고 확인 (비관적 락)
     * ② originalAmount 계산
     * ③ 쿠폰 적용
     * ④ 재고 차감 (dirty checking)
     * ⑤ 주문 생성 (금액 스냅샷 포함, BR-O13)
     *
     * 이벤트 발행 이후의 부가 로직은 Listener에서 처리:
     * - BEFORE_COMMIT: Outbox 테이블에 기록 (Kafka 발행 보장)
     * - AFTER_COMMIT: 로깅 등 부가 처리
     */
    @Transactional
    public OrderInfo create(Long userId, OrderCreateCommand command) {
        // 수량 VO 변환 (Quantity 생성자에서 >= 1 검증, BR-O02)
        Map<Long, Quantity> quantityByProductId = command.items().stream()
                .collect(Collectors.toMap(
                        OrderCreateCommand.Item::productId,
                        item -> new Quantity(item.quantity()),
                        (existing, duplicate) -> {
                            throw new CoreException(ErrorType.BAD_REQUEST, "동일 상품은 한 번만 주문할 수 있습니다.");
                        }
                ));

        // ① 비관적 락으로 재고 확인만 (차감 X). 이 시점부터 락 보유 (BR-O03)
        List<Product> products = productService.findAllAndVerifyStock(quantityByProductId);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // ② originalAmount 계산 (쿠폰 적용 전 총 금액, BR-O11 기준)
        int originalAmountValue = command.items().stream()
                .mapToInt(item -> {
                    Product product = productMap.get(item.productId());
                    return product.getPrice().getAmount() * item.quantity();
                })
                .sum();
        Money originalAmount = new Money(originalAmountValue);

        // ③ 쿠폰 적용 (BR-O09: 선택적). 유효성 검증 + 사용 처리 + 할인 금액 계산은 CouponService 책임
        Long userCouponId = command.userCouponId();
        Money discountAmount = new Money(0);

        if (userCouponId != null) {
            int discount = couponService.applyCoupon(userCouponId, userId, originalAmountValue);
            discountAmount = new Money(discount);
        }

        // ④ 재고 차감 (dirty checking, 비관적 락 범위 내)
        for (Product product : products) {
            Quantity quantity = quantityByProductId.get(product.getId());
            product.decreaseStock(quantity);
        }
        // 재고 변동 → 주문된 각 상품 상세 캐시 즉시 무효화 (커밋 후 처리)
        products.forEach(p -> productEventPublisher.publish(new ProductDetailCacheEvictEvent(p.getId())));

        // 브랜드명 일괄 조회 (스냅샷용)
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        // OrderItem 스냅샷 구성 (BR-O05)
        List<OrderItem> orderItems = command.items().stream()
                .map(item -> {
                    Product product = productMap.get(item.productId());
                    String brandName = brandNameMap.get(product.getBrandId());
                    return new OrderItem(
                            product.getId(),
                            new Quantity(item.quantity()),
                            product.getName(),
                            brandName,
                            product.getPrice()
                    );
                })
                .toList();

        // ⑤ 주문 저장 (금액 스냅샷 포함, BR-O13)
        Order order = orderService.create(userId, orderItems, userCouponId, originalAmount, discountAmount);

        // 주문 생성 이벤트 발행 (이후 처리는 Listener가 담당)
        List<OrderCreatedEvent.OrderItemSnapshot> itemSnapshots = orderItems.stream()
                .map(item -> new OrderCreatedEvent.OrderItemSnapshot(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity().getValue(),
                        item.getPrice().getAmount()
                ))
                .toList();
        orderEventPublisher.publish(new OrderCreatedEvent(
                order.getId(), userId, order.getFinalAmount().getAmount(), itemSnapshots));
        // 유저 행동 로깅
        userActionEventPublisher.publish(new UserActionEvent(
                UserActionEvent.ActionType.ORDER_CREATE, userId, "ORDER", order.getId(), null));

        return OrderInfo.of(order);
    }

    /**
     * 회원 주문 상세 조회 (US-O03)
     * 존재 확인 → 소유권 확인 (BR-O06)
     */
    @Transactional(readOnly = true)
    public OrderInfo findById(Long orderId, Long userId) {
        Order order = orderService.findById(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다.");
        }
        return OrderInfo.of(order);
    }

    /**
     * 회원 주문 목록 조회 (US-O02)
     * 기간 필터 + 소유권 제한 (BR-O06, BR-O08)
     */
    @Transactional(readOnly = true)
    public List<OrderInfo> findAllByUserId(Long userId, LocalDate startAt, LocalDate endAt) {
        ZonedDateTime from = startAt.atStartOfDay(KST);
        ZonedDateTime to = endAt.plusDays(1).atStartOfDay(KST);
        return orderService.findAllByUserId(userId, from, to).stream()
                .map(OrderInfo::of)
                .toList();
    }
}
