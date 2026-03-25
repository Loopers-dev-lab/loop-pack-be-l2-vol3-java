package com.loopers.application.order;

import com.loopers.application.cache.OrderCacheManager;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.common.CursorResult;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentResult;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgApproveRequest;
import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import com.loopers.support.error.OrderErrorType;
import com.loopers.support.error.PointErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 주문 Facade
 *
 * Order + Address + Product + Brand + Inventory + Cart + Coupon + Point + Payment
 * 도메인 서비스를 조합하여 주문 생성~결제 확정을 처리한다.
 *
 * 트랜잭션 분리 전략:
 * TX1 (예약): 재고 예약 + 주문 생성 + 쿠폰 사용 + 포인트 차감 + Payment 생성 → 커밋 (락 해제)
 * PG 결제: 트랜잭션 밖 (외부 시스템 호출 — 락 미보유)
 * TX2-성공: Payment 승인 + 재고 확정 + 주문 확정 + 포인트 적립
 * TX2-실패: 보상 트랜잭션 (쿠폰 복원 + 포인트 환급 + 재고 해제 + 주문 취소)
 */
@Component
public class OrderFacade {

    private static final Logger log = LoggerFactory.getLogger(OrderFacade.class);

    private final OrderService orderService;
    private final UserAddressService userAddressService;
    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;
    private final CartItemService cartItemService;
    private final CouponService couponService;
    private final PointService pointService;
    private final PaymentService paymentService;
    private final PaymentFacade paymentFacade;
    private final TransactionTemplate txTemplate;
    private final OrderCacheManager orderCacheManager;

    public OrderFacade(OrderService orderService, UserAddressService userAddressService,
                       ProductService productService, BrandService brandService,
                       InventoryService inventoryService, CartItemService cartItemService,
                       CouponService couponService, PointService pointService,
                       PaymentService paymentService, PaymentFacade paymentFacade,
                       PlatformTransactionManager txManager,
                       OrderCacheManager orderCacheManager) {
        this.orderService = orderService;
        this.userAddressService = userAddressService;
        this.productService = productService;
        this.brandService = brandService;
        this.inventoryService = inventoryService;
        this.cartItemService = cartItemService;
        this.couponService = couponService;
        this.pointService = pointService;
        this.paymentService = paymentService;
        this.paymentFacade = paymentFacade;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setTimeout(30);
        this.orderCacheManager = orderCacheManager;
    }

    /**
     * 주문 생성 + 결제 확정 (트랜잭션 분리)
     */
    public OrderCreateResult createOrder(Long userId, String userName, String ordererPhone,
                                          List<OrderItemCommand> itemCommands, Long addressId,
                                          Long issuedCouponId, int pointAmount, String paymentMethod,
                                          String cardNo) {
        OrderPaymentContext context = reserveAndCreateOrder(
                userId, userName, ordererPhone, itemCommands, addressId,
                issuedCouponId, pointAmount, paymentMethod);

        OrderCreateResult result = processPaymentAndConfirm(context, cardNo);

        orderCacheManager.evictOrderList(userId);
        return result;
    }

    /**
     * 장바구니 기반 주문 생성 + 결제 확정 (트랜잭션 분리)
     */
    public OrderCreateResult createOrderFromCart(Long userId, String userName, String ordererPhone,
                                                  List<Long> cartItemIds, Long addressId,
                                                  Long issuedCouponId, int pointAmount, String paymentMethod,
                                                  String cardNo) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new CoreException(OrderErrorType.EMPTY_ORDER_ITEMS);
        }

        List<CartItem> cartItems = cartItemService.getCartItemsByIds(cartItemIds, userId);
        List<OrderItemCommand> itemCommands = cartItems.stream()
                .map(item -> new OrderItemCommand(item.getProductId(), item.getQuantity()))
                .toList();

        OrderPaymentContext context = reserveAndCreateOrder(
                userId, userName, ordererPhone, itemCommands, addressId,
                issuedCouponId, pointAmount, paymentMethod);

        OrderCreateResult result = processPaymentAndConfirm(context, cardNo);

        // 장바구니 삭제는 best-effort — 실패해도 주문 성공 응답을 유지한다
        try {
            txTemplate.executeWithoutResult(status -> cartItemService.deleteAll(cartItemIds, userId));
        } catch (Exception e) {
            log.warn("장바구니 삭제 실패 — 주문은 정상 완료 (orderId={})", result.orderId(), e);
        }

        orderCacheManager.evictOrderList(userId);
        return result;
    }

    /**
     * TX1: 주문 예약 (재고 예약 + 주문 생성 + 쿠폰 사용 + 포인트 차감 + Payment 생성)
     *
     * 이 트랜잭션이 커밋되면 비관적 락(재고)이 해제된다.
     * PG 호출 대기 중에도 다른 주문이 재고를 확보할 수 있다.
     */
    private OrderPaymentContext reserveAndCreateOrder(Long userId, String userName, String ordererPhone,
                                                       List<OrderItemCommand> itemCommands, Long addressId,
                                                       Long issuedCouponId, int pointAmount, String paymentMethod) {
        return txTemplate.execute(status -> {
            if (itemCommands == null || itemCommands.isEmpty()) {
                throw new CoreException(OrderErrorType.EMPTY_ORDER_ITEMS);
            }

            UserAddress address = userAddressService.getAddress(addressId, userId);

            // 상품 검증 + OrderItem 스냅샷 생성
            Map<Long, Integer> productQtyMap = itemCommands.stream()
                    .collect(Collectors.toMap(OrderItemCommand::productId, OrderItemCommand::quantity, Integer::sum));

            List<OrderItem> orderItems = new ArrayList<>();
            for (Map.Entry<Long, Integer> entry : productQtyMap.entrySet()) {
                Product product = productService.getDisplayableProduct(entry.getKey());
                product.assertPurchasable();

                Brand brand = brandService.getById(product.getBrandId());

                orderItems.add(OrderItem.snapshot(
                        product.getId(),
                        product.getName(),
                        brand.getName(),
                        product.getBasePrice(),
                        entry.getValue()
                ));
            }

            int subtotal = orderItems.stream().mapToInt(OrderItem::getLineTotal).sum();

            // 1. 쿠폰 검증 + 할인 계산 (읽기만, 락 없음)
            int discountAmount = 0;
            if (issuedCouponId != null) {
                IssuedCoupon issuedCoupon = couponService.getIssuedCoupon(issuedCouponId, userId);
                CouponTemplate template = couponService.getTemplate(issuedCoupon.getCouponTemplateId());
                if (!template.isApplicable(subtotal, ZonedDateTime.now())) {
                    throw new CoreException(CouponErrorType.INVALID_TEMPLATE);
                }
                discountAmount = template.calculateDiscount(subtotal);
            }

            // 2. 포인트 잔액 검증 (읽기만, 락 없음)
            if (pointAmount > 0) {
                PointAccount account = pointService.getAccount(userId);
                if (account.getBalance() < pointAmount) {
                    throw new CoreException(PointErrorType.INSUFFICIENT_BALANCE);
                }
            }

            // 3. 재고 예약 (비관적 락)
            inventoryService.reserveAll(productQtyMap);

            // 4. 주문 생성 (할인 적용 포함, 단일 save)
            String orderNumber = generateOrderNumber();
            Order order = orderService.createWithDiscount(
                    userId, orderNumber, orderItems,
                    userName, ordererPhone,
                    address.getReceiverName(), address.getPhone(),
                    address.getZipCode(), address.getAddressLine1(), address.getAddressLine2(),
                    discountAmount, pointAmount, 0, issuedCouponId);

            // 5. 쿠폰 사용 (원자적 UPDATE)
            if (issuedCouponId != null) {
                couponService.use(issuedCouponId, userId, order.getId());
            }

            // 6. 포인트 차감 (원자적 UPDATE)
            if (pointAmount > 0) {
                pointService.use(userId, pointAmount);
            }

            // 7. Payment 생성 (REQUESTED)
            String idempotencyKey = generateIdempotencyKey();
            Payment payment = paymentService.create(
                    order.getId(), order.getTotalAmount(), paymentMethod, idempotencyKey);

            return new OrderPaymentContext(
                    order.getId(), order.getOrderNumber(), order.getTotalAmount(),
                    payment.getId(), userId, issuedCouponId, pointAmount,
                    paymentMethod, productQtyMap);
        });
    }

    /**
     * PG 결제 요청 + 결과에 따른 즉시 처리 또는 콜백 대기
     *
     * PG 호출은 트랜잭션 밖에서 수행되어 락 보유 시간을 최소화한다.
     * - APPROVED: 즉시 TX2 실행 (PaymentFacade.confirmPayment)
     * - FAILED: 즉시 보상 실행 (PaymentFacade.compensatePayment)
     * - PENDING: 콜백 대기 (아무것도 안 함)
     * - UNKNOWN: 보상하지 않고 대기 (콜백 또는 대사 배치에서 처리)
     */
    private OrderCreateResult processPaymentAndConfirm(OrderPaymentContext context, String cardNo) {
        try {
            // PG 결제 (트랜잭션 밖 — PaymentService가 PG 호출 + 결과 해석을 캡슐화)
            PgApproveRequest pgRequest = new PgApproveRequest(
                    context.userId(),
                    context.orderNumber(),
                    context.paymentMethod(),
                    cardNo,
                    context.totalAmount(),
                    null
            );
            Payment payment = paymentService.getById(context.paymentId());
            PaymentResult pgResult = paymentService.requestPayment(payment, pgRequest);

            if (pgResult.isApproved()) {
                paymentFacade.confirmPayment(context.orderId(), pgResult.transactionKey());
            } else if (pgResult.isFailed()) {
                paymentFacade.compensatePayment(context.orderId());
            } else if (pgResult.isUnknown()) {
                // UNKNOWN — 보상하지 않고 대기 (콜백 또는 대사 배치에서 처리)
                txTemplate.executeWithoutResult(status ->
                        paymentService.markUnknown(context.paymentId()));
                log.warn("PG 결제 결과 불확실 — 콜백/대사 배치 대기 (orderId={})", context.orderId());
            }
            // PENDING — 콜백 대기 (아무것도 안 함)

            return txTemplate.execute(status -> {
                Order order = orderService.getById(context.orderId());
                return new OrderCreateResult(
                        order.getId(), order.getOrderNumber(), order.getStatus().name(),
                        order.getTotalAmount(), order.getPaymentId());
            });
        } catch (Exception e) {
            paymentFacade.compensatePayment(context.orderId());
            throw e;
        }
    }

    /**
     * 주문 취소 (F-16)
     *
     * 주문은 "당시 스냅샷" 기준의 독립 도메인이다.
     * 상품/재고 삭제 여부와 관계없이 주문 취소는 항상 성공해야 한다.
     *
     * 1. OrderService.cancel → 소유권 검증 + PENDING→CANCELED
     * 2. 재고 예약 해제 (order_items 기준으로 reserved_qty 복구)
     *    - 삭제된 상품의 재고는 skip (InventoryService.releaseAll 내부 처리)
     */
    @Transactional(timeout = 30)
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderService.cancel(orderId, userId);

        Map<Long, Integer> productQtyMap = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
        inventoryService.releaseAll(productQtyMap);

        orderCacheManager.registerEvictAfterCommit(userId);
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateIdempotencyKey() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * 주문 목록 커서 조회 — 기본 조회(첫 페이지 + 커스텀 기간 없음)만 Cache-Aside
     *
     * @param isDefaultQuery Controller에서 판단: startAt/endAt/cursor 모두 미지정 시 true
     */
    public OrderCursorResult getOrdersWithCursor(Long userId, ZonedDateTime startAt, ZonedDateTime endAt,
                                                  ZonedDateTime cursorCreatedAt, Long cursorId, int size,
                                                  boolean isDefaultQuery) {
        if (isDefaultQuery) {
            java.util.Optional<OrderCursorResult> cached = orderCacheManager.getOrderList(userId);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        CursorResult<Order> result = orderService.getOrdersWithCursor(userId, startAt, endAt, cursorCreatedAt, cursorId, size);

        List<OrderSummaryResult> summaries = result.items().stream()
                .map(o -> new OrderSummaryResult(
                        o.getId(), o.getOrderNumber(), o.getStatus().name(),
                        o.getTotalAmount(), o.getCreatedAt()))
                .toList();

        OrderCursorResult cursorResult = new OrderCursorResult(summaries, result.hasNext(), size);

        if (isDefaultQuery) {
            orderCacheManager.putOrderList(userId, cursorResult);
        }

        return cursorResult;
    }

    /** 주문 상세 조회 */
    @Transactional(readOnly = true)
    public OrderDetailResult getOrderDetail(Long orderId, Long userId) {
        Order order = orderService.getOrder(orderId, userId);

        List<OrderItemDetailResult> items = order.getItems().stream()
                .map(item -> new OrderItemDetailResult(
                        item.getProductName(), item.getBrandName(),
                        item.getUnitPrice(), item.getQuantity(), item.getLineTotal()))
                .toList();

        return new OrderDetailResult(
                order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getOrdererName(), order.getOrdererPhone(),
                order.getReceiverName(), order.getReceiverPhone(),
                order.getZipCode(), order.getAddressLine1(), order.getAddressLine2(),
                order.getSubtotalAmount(), order.getDiscountAmount(),
                order.getPointUsedAmount(), order.getShippingFee(), order.getTotalAmount(),
                items, order.getCreatedAt());
    }

    private record OrderPaymentContext(
            Long orderId, String orderNumber, int totalAmount,
            Long paymentId, Long userId, Long issuedCouponId, int pointAmount,
            String paymentMethod, Map<Long, Integer> productQtyMap) {}

    public record OrderCreateResult(
            Long orderId, String orderNumber, String status,
            int totalAmount, Long paymentId) {}

    public record OrderItemCommand(Long productId, int quantity) {}

    public record OrderCursorResult(
            List<OrderSummaryResult> orders,
            boolean hasNext,
            int size
    ) {}

    public record OrderSummaryResult(
            Long orderId, String orderNumber, String status,
            int totalAmount, ZonedDateTime createdAt) {}

    public record OrderDetailResult(
            Long orderId, String orderNumber, String status,
            String ordererName, String ordererPhone,
            String receiverName, String receiverPhone,
            String zipCode, String addressLine1, String addressLine2,
            int subtotalAmount, int discountAmount,
            int pointUsedAmount, int shippingFee, int totalAmount,
            List<OrderItemDetailResult> items, ZonedDateTime createdAt) {}

    public record OrderItemDetailResult(
            String productName, String brandName,
            int unitPrice, int quantity, int lineTotal) {}
}
