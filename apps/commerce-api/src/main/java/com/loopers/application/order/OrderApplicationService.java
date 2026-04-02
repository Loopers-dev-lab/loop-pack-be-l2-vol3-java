package com.loopers.application.order;

import com.loopers.application.event.OrderCompletedEvent;
import com.loopers.application.event.UserActionEvent;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.point.UserPoint;
import com.loopers.domain.point.UserPointRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.useraction.ActionType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 Application Service.
 * 여러 BC 조합 및 트랜잭션 경계 담당.
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final UserPointRepository userPointRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        // 5) 이벤트 발행
        publishOrderEvents(saved, userId, items);

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

    /**
     * 쿠폰/포인트 할인이 적용된 주문 생성.
     *
     * @param userId 사용자 ID
     * @param request 주문 요청 (쿠폰, 포인트 포함)
     * @return 생성된 주문 결과
     */
    @Transactional
    public OrderResult placeOrderWithDiscount(Long userId, PlaceOrderWithDiscountRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        // 1. 쿠폰 조회 및 락 (nullable)
        IssuedCoupon coupon = null;
        CouponTemplate template = null;
        if (request.hasCoupon()) {
            coupon = issuedCouponRepository.findByIdWithLock(request.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

            if (!coupon.getUserId().equals(userId)) {
                throw new CoreException(ErrorType.COUPON_ACCESS_DENIED);
            }
            if (!coupon.isUsable()) {
                throw new CoreException(ErrorType.COUPON_NOT_AVAILABLE);
            }

            template = couponTemplateRepository.findById(coupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        }

        // 2. 상품 조회 및 재고 차감 (비관적 락)
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemRequest req : request.items()) {
            Product product = productRepository.findByIdWithLock(req.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            product.decreaseStock(req.quantity());
            productRepository.save(product);

            orderItems.add(OrderItem.create(
                product.getId(),
                product.getName(),
                req.quantity(),
                product.getPrice()
            ));
        }

        // 3. 원가 계산
        Money originalAmount = orderItems.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO, Money::add);

        // 4. 쿠폰 할인액 계산
        Money couponDiscount = Money.ZERO;
        if (coupon != null && template != null) {
            // 최소 주문 금액 검증
            if (originalAmount.amount() < template.getMinOrderAmount().amount()) {
                throw new CoreException(ErrorType.ORDER_AMOUNT_TOO_LOW);
            }
            couponDiscount = template.calculateDiscount(originalAmount);
        }

        // 5. 포인트 차감 (비관적 락)
        Money pointDiscount = Money.ZERO;
        if (request.hasPoint()) {
            UserPoint userPoint = userPointRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new CoreException(ErrorType.POINT_NOT_FOUND));

            userPoint.use(request.pointAmount());
            userPointRepository.save(userPoint);
            pointDiscount = new Money(request.pointAmount());
        }

        // 6. 쿠폰 사용 처리
        Long couponId = null;
        if (coupon != null) {
            coupon.use();
            issuedCouponRepository.save(coupon);
            couponId = coupon.getId();
        }

        // 7. 주문 생성
        Order order = Order.createWithDiscount(userId, orderItems, couponId, couponDiscount, pointDiscount);
        Order saved = orderRepository.save(order);

        // 8. 이벤트 발행
        publishOrderEvents(saved, userId, request.items());

        return OrderResult.from(saved);
    }

    private void publishOrderEvents(Order order, Long userId, List<OrderItemRequest> items) {
        int totalQuantity = items.stream().mapToInt(OrderItemRequest::quantity).sum();
        Long firstProductId = items.isEmpty() ? null : items.get(0).productId();

        eventPublisher.publishEvent(OrderCompletedEvent.of(
            order.getId(),
            userId,
            firstProductId,
            totalQuantity,
            order.getTotalPrice().amount()
        ));
        eventPublisher.publishEvent(UserActionEvent.of(userId, ActionType.ORDER, order.getId()));
    }
}
