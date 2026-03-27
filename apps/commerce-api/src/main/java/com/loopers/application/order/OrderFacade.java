package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponPendingActionService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderCartRestoreModel;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.event.OrderCancelledEvent;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.order.event.OrderExpiredEvent;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.OrderType;
import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 주문 Facade (퍼사드)
 *
 * <p>OrderService, ProductService, BrandService, StockService, CartService, CouponService를
 * 조합(orchestration)하여 주문 비즈니스 플로우를 완성한다.</p>
 *
 * <ul>
 *   <li>트랜잭션 경계 설정</li>
 *   <li>직접 주문(DIRECT) 및 장바구니 주문(CART) 생성 — 쿠폰 검증, 상품 검증, 낙관적 재고 확인, 스냅샷 생성</li>
 *   <li>주문 취소/만료 — CAS 상태 전이 후 쿠폰/장바구니 복원</li>
 *   <li>주문 목록 조회, 상세 조회</li>
 * </ul>
 *
 * <h3>Optimistic Stock Check 패턴</h3>
 * <p>
 * 주문 생성 시 재고를 hold(예약)하지 않고 읽기 전용 확인만 수행한다.
 * 실제 재고 hold는 결제 요청 시점({@link com.loopers.application.payment.PaymentFacade})에서 수행하여
 * hold 기간을 PG 응답 시간(수 초)으로 최소화한다.
 * </p>
 *
 * <p>인증은 {@link com.loopers.interfaces.api.CustomerAuthInterceptor}에서 처리된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;
    private final CartService cartService;
    private final CouponService couponService;
    private final CouponPendingActionService couponPendingActionService;
    private final PaymentService paymentService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 직접 주문을 생성한다.
     *
     * @param userId       사용자 ID
     * @param items        주문할 상품 항목 목록
     * @param userCouponId 적용할 발급 쿠폰 ID (없으면 null)
     * @return 생성된 주문 정보
     */
    @Transactional
    public OrderInfo createDirectOrder(Long userId, List<OrderItemCommand> items, Long userCouponId) {
        return processOrder(userId, OrderType.DIRECT, items, userCouponId);
    }

    /**
     * 장바구니 주문을 생성한다.
     *
     * @param userId       사용자 ID
     * @param items        주문할 상품 항목 목록
     * @param userCouponId 적용할 발급 쿠폰 ID (없으면 null)
     * @return 생성된 주문 정보
     */
    @Transactional
    public OrderInfo createCartOrder(Long userId, List<OrderItemCommand> items, Long userCouponId) {
        return processOrder(userId, OrderType.CART, items, userCouponId);
    }

    /**
     * 주문 목록을 조회한다 (기간 필터).
     */
    public List<OrderInfo> getOrders(Long userId, LocalDateTime start, LocalDateTime end) {
        List<OrderModel> orders = orderService.findAllByUserId(userId, start, end);
        Map<Long, List<OrderItemModel>> itemMap = orderService.batchLoadOrderItems(orders);
        return orders.stream()
                .map(order -> OrderInfo.from(order, itemMap.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }

    /**
     * 주문 상세 정보를 조회한다.
     */
    public OrderInfo getOrderDetail(Long userId, Long orderId) {
        OrderModel order = orderService.findByIdAndUserId(orderId, userId);
        List<OrderItemModel> items = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, items);
    }

    /**
     * 관리자용 전체 주문 목록을 조회한다 (기간 필터).
     */
    public List<OrderInfo> getOrdersForAdmin(LocalDateTime start, LocalDateTime end) {
        List<OrderModel> orders = orderService.findAllOrders(start, end);
        Map<Long, List<OrderItemModel>> itemMap = orderService.batchLoadOrderItems(orders);
        return orders.stream()
                .map(order -> OrderInfo.from(order, itemMap.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }

    /**
     * 관리자용 주문 상세 정보를 조회한다.
     */
    public OrderInfo getOrderDetailForAdmin(Long orderId) {
        OrderModel order = orderService.findOrderById(orderId);
        List<OrderItemModel> items = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, items);
    }

    /**
     * 주문 생성 공통 로직.
     * <p>
     * Phase 1: 읽기 + 검증 + 낙관적 재고 확인 → Phase 2: 할인 + 저장.
     * 재고 hold는 수행하지 않으며, 결제 요청 시점에 CAS hold가 수행된다.
     * </p>
     */
    private OrderInfo processOrder(Long userId, OrderType orderType, List<OrderItemCommand> items,
                                    Long userCouponId) {
        List<OrderItemCommand> merged = orderService.validateAndPrepare(userId, items);

        // Phase 1: 읽기 + 검증 + 낙관적 재고 확인 (hold 없음 — 보상 불필요)
        UserCouponModel userCoupon = resolveCoupon(userId, userCouponId);
        CouponModel coupon = userCoupon != null
                ? couponService.findByIdForAdmin(userCoupon.getCouponId()) : null;

        // 쿠폰 CAS 선점 (AVAILABLE → RESERVED)
        if (userCoupon != null) {
            couponService.reserveCoupon(userCoupon.getUserCouponId());
        }

        List<OrderItemSnapshot> snapshots = buildSnapshots(merged);
        BigDecimal totalOriginal = sumOriginalAmounts(snapshots);

        if (coupon != null) {
            coupon.validateApplicable(totalOriginal);
        }

        // 낙관적 재고 확인 (읽기만, hold 없음 — 최종 보장은 결제 시 CAS hold)
        validateStockAvailability(merged);

        // Phase 2: 할인 계산 + 주문 저장
        BigDecimal totalDiscount = coupon != null
                ? coupon.calculateDiscount(totalOriginal) : BigDecimal.ZERO;
        List<OrderItemSnapshot> finalSnapshots = coupon != null
                ? coupon.distributeDiscount(snapshots, totalDiscount) : snapshots;

        OrderModel order = orderService.createOrder(userId, orderType,
                totalOriginal.subtract(totalDiscount), finalSnapshots);

        if (userCoupon != null) {
            couponPendingActionService.saveConfirm(userCoupon.getUserCouponId(), order.getOrderId());
        }

        List<OrderItemModel> orderItems = orderService.findOrderItems(order.getOrderId());
        OrderInfo info = OrderInfo.from(order, orderItems);

        // Step 2: Outbox 기록 (같은 TX — 주문과 원자적 저장)
        OrderCreatedEvent createdEvent = OrderCreatedEvent.from(order, orderItems);
        outboxEventService.save(
            "ORDER", String.valueOf(info.getOrderId()),
            "ORDER_CREATED", "order-events",
            String.valueOf(info.getOrderId()),
            toJson(createdEvent)
        );

        // 이벤트 발행
        eventPublisher.publishEvent(createdEvent);

        return info;
    }

    /**
     * 쿠폰을 검증하고 조회한다. userCouponId가 null이면 null을 반환한다.
     */
    private UserCouponModel resolveCoupon(Long userId, Long userCouponId) {
        if (userCouponId == null) {
            return null;
        }
        return couponService.validateAndGetUserCoupon(userId, userCouponId);
    }

    /**
     * 주문 항목별 상품/브랜드를 조회하여 스냅샷 목록을 생성한다.
     */
    private List<OrderItemSnapshot> buildSnapshots(List<OrderItemCommand> merged) {
        return merged.stream()
                .map(item -> {
                    ProductModel product = productService.findOrderableById(item.productId());
                    BrandModel brand = brandService.findById(product.getBrandId());
                    return OrderItemSnapshot.from(product, brand, item.quantity());
                })
                .toList();
    }

    private BigDecimal sumOriginalAmounts(List<OrderItemSnapshot> snapshots) {
        return snapshots.stream()
                .map(OrderItemSnapshot::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 낙관적 재고 확인 (읽기 전용, hold 없음).
     * <p>
     * 주문 생성 시점의 사전 필터로, 재고 부족 상품의 주문 생성을 조기 차단한다.
     * 최종 재고 보장은 결제 요청 시점의 CAS hold가 담당한다.
     * </p>
     */
    private void validateStockAvailability(List<OrderItemCommand> merged) {
        for (OrderItemCommand item : merged) {
            stockService.validateAvailability(item.productId(), item.quantity());
        }
    }

    /**
     * 주문을 취소한다.
     * <p>
     * 결제가 진행 중(REQUESTED 상태)이면 취소를 거부한다.
     * PG에서 결제가 승인된 후 주문이 취소되면 상태 불일치가 발생하므로,
     * 결제 진행 중에는 반드시 결제 완료/실패를 기다린 후 취소해야 한다.
     * </p>
     * <p>
     * 재고 release는 수행하지 않는다. 결제 시도 전이면 hold 없음,
     * 결제 시도 후면 PaymentFacade에서 release를 담당한다.
     * </p>
     */
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        if (paymentService.hasActivePayment(orderId)) {
            throw new CoreException(ErrorType.ORDER_NOT_CANCELLABLE,
                    "결제가 진행 중인 주문은 취소할 수 없습니다");
        }
        Optional<OrderModel> order = orderService.cancelOrder(userId, orderId);
        order.ifPresent(o -> {
            restoreOrderResources(o, RestoreReason.USER_CANCELLED, RestoreTriggerSource.CANCEL_API);

            OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(o.getOrderId(), userId);

            // Step 2: Outbox 기록
            outboxEventService.save(
                "ORDER", String.valueOf(o.getOrderId()),
                "ORDER_CANCELLED", "order-events",
                String.valueOf(o.getOrderId()),
                toJson(cancelledEvent)
            );

            eventPublisher.publishEvent(cancelledEvent);
        });
    }

    /**
     * 배치 스케줄러가 주문을 만료 처리한다.
     */
    @Transactional
    public void expireOrder(Long orderId) {
        expireOrder(orderId, RestoreReason.EXPIRED, RestoreTriggerSource.EXPIRE_JOB);
    }

    /**
     * 주문을 만료 처리한다. 사유와 트리거 출처를 명시할 수 있다.
     * <p>
     * 결제 전체 실패, 배치 만료 등 다양한 경로에서 호출된다.
     * CAS 기반이므로 이미 만료/취소된 주문에는 영향 없다 (멱등).
     * </p>
     *
     * @param orderId       주문 ID
     * @param reason        복원 사유
     * @param triggerSource 트리거 출처
     */
    @Transactional
    public void expireOrder(Long orderId, RestoreReason reason, RestoreTriggerSource triggerSource) {
        Optional<OrderModel> order = orderService.expireOrder(orderId);
        order.ifPresent(o -> {
            restoreOrderResources(o, reason, triggerSource);

            OrderExpiredEvent expiredEvent = new OrderExpiredEvent(o.getOrderId(), o.getUserId());

            // Step 2: Outbox 기록
            outboxEventService.save(
                "ORDER", String.valueOf(o.getOrderId()),
                "ORDER_EXPIRED", "order-events",
                String.valueOf(o.getOrderId()),
                toJson(expiredEvent)
            );

            eventPublisher.publishEvent(expiredEvent);
        });
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }

    /**
     * 쿠폰 및 장바구니 복원을 수행한다.
     * <p>
     * 재고 release는 수행하지 않는다. Optimistic Stock 패턴에서
     * 재고 hold/release는 PaymentFacade가 담당한다.
     * </p>
     */
    private void restoreOrderResources(OrderModel order, RestoreReason reason,
                                        RestoreTriggerSource triggerSource) {
        // 쿠폰 복원 (멱등)
        couponService.restoreCoupon(order.getOrderId());

        if (order.getOrderType() == OrderType.DIRECT) {
            if (!orderService.existsCartRestore(order.getOrderId())) {
                List<OrderItemModel> orderItems = orderService.findOrderItems(order.getOrderId());
                orderService.saveCartRestore(
                        OrderCartRestoreModel.create(order.getOrderId(), order.getUserId(),
                                reason, triggerSource));
                List<CartService.RestoreItem> restoreItems = orderItems.stream()
                        .map(item -> new CartService.RestoreItem(item.getProductId(), item.getQuantity()))
                        .toList();
                cartService.restoreFromOrder(order.getUserId(), restoreItems);
            }
        }
    }
}
