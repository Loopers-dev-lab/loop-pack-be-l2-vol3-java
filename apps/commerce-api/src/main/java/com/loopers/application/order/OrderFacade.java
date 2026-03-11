package com.loopers.application.order;

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
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
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
    private final TransactionTemplate txTemplate;

    public OrderFacade(OrderService orderService, UserAddressService userAddressService,
                       ProductService productService, BrandService brandService,
                       InventoryService inventoryService, CartItemService cartItemService,
                       CouponService couponService, PointService pointService,
                       PaymentService paymentService,
                       PlatformTransactionManager txManager) {
        this.orderService = orderService;
        this.userAddressService = userAddressService;
        this.productService = productService;
        this.brandService = brandService;
        this.inventoryService = inventoryService;
        this.cartItemService = cartItemService;
        this.couponService = couponService;
        this.pointService = pointService;
        this.paymentService = paymentService;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setTimeout(30);
    }

    /**
     * 주문 생성 + 결제 확정 (트랜잭션 분리)
     */
    public OrderCreateResult createOrder(Long userId, String userName, String ordererPhone,
                                          List<OrderItemCommand> itemCommands, Long addressId,
                                          Long issuedCouponId, int pointAmount, String paymentMethod) {
        OrderPaymentContext context = reserveAndCreateOrder(
                userId, userName, ordererPhone, itemCommands, addressId,
                issuedCouponId, pointAmount, paymentMethod);

        return processPaymentAndConfirm(context);
    }

    /**
     * 장바구니 기반 주문 생성 + 결제 확정 (트랜잭션 분리)
     */
    public OrderCreateResult createOrderFromCart(Long userId, String userName, String ordererPhone,
                                                  List<Long> cartItemIds, Long addressId,
                                                  Long issuedCouponId, int pointAmount, String paymentMethod) {
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

        OrderCreateResult result = processPaymentAndConfirm(context);

        // 장바구니 삭제는 best-effort — 실패해도 주문 성공 응답을 유지한다
        try {
            txTemplate.executeWithoutResult(status -> cartItemService.deleteAll(cartItemIds, userId));
        } catch (Exception e) {
            log.warn("장바구니 삭제 실패 — 주문은 정상 완료 (orderId={})", result.orderId(), e);
        }

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
     * PG 결제 + TX2 (확정 또는 보상)
     *
     * PG 호출은 트랜잭션 밖에서 수행되어 락 보유 시간을 최소화한다.
     * PG 실패 또는 확정 실패 시 보상 트랜잭션으로 TX1의 변경을 되돌린다.
     */
    private OrderCreateResult processPaymentAndConfirm(OrderPaymentContext context) {
        try {
            // PG 결제 (트랜잭션 밖 — 락 미보유 상태에서 외부 호출)
            String pgTxnId = simulatePgPayment();

            // TX2: 결제 확정 + 재고 확정 + 주문 확정 + 포인트 적립
            return txTemplate.execute(status -> {
                paymentService.approve(context.paymentId(), pgTxnId, context.totalAmount());
                inventoryService.commitAll(context.productQtyMap());
                orderService.confirm(context.orderId(), context.paymentId(), context.paymentMethod());
                pointService.earn(context.userId(), context.totalAmount());

                Order order = orderService.getById(context.orderId());
                return new OrderCreateResult(
                        order.getId(), order.getOrderNumber(), order.getStatus().name(),
                        order.getTotalAmount(), order.getPaymentId());
            });
        } catch (Exception e) {
            compensateOrder(context);
            throw e;
        }
    }

    /**
     * 보상 트랜잭션: TX1에서 커밋된 변경을 되돌린다.
     *
     * - Payment → FAILED
     * - 쿠폰 → ISSUED 복원 (USED → ISSUED)
     * - 포인트 → 환급 (차감 금액 반환)
     * - 재고 → 예약 해제
     * - 주문 → CANCELED
     *
     * 보상 자체가 실패하면 CRITICAL 로그를 남기고 수동 복구가 필요하다.
     */
    private void compensateOrder(OrderPaymentContext context) {
        try {
            txTemplate.executeWithoutResult(status -> {
                paymentService.fail(context.paymentId());

                if (context.issuedCouponId() != null) {
                    couponService.restore(context.issuedCouponId(), context.orderId());
                }
                if (context.pointAmount() > 0) {
                    pointService.refund(context.userId(), context.pointAmount());
                }

                inventoryService.releaseAll(context.productQtyMap());
                orderService.cancel(context.orderId(), context.userId());
            });
        } catch (Exception compensateEx) {
            log.error("CRITICAL: 보상 트랜잭션 실패 — 수동 복구 필요 (orderId={}, paymentId={})",
                    context.orderId(), context.paymentId(), compensateEx);
        }
    }

    /**
     * PG 결제 시뮬레이션 — 항상 성공
     * 실제 PG 연동 시 이 메서드를 외부 PG API 호출로 교체한다.
     */
    private String simulatePgPayment() {
        return "PG-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateIdempotencyKey() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /** 주문 목록 조회 */
    @Transactional(readOnly = true)
    public OrderListResult getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        List<Order> orders = orderService.getOrders(userId, startAt, endAt);

        List<OrderSummaryResult> summaries = orders.stream()
                .map(o -> new OrderSummaryResult(
                        o.getId(), o.getOrderNumber(), o.getStatus().name(),
                        o.getTotalAmount(), o.getCreatedAt()))
                .toList();

        return new OrderListResult(summaries);
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

    public record OrderListResult(List<OrderSummaryResult> orders) {}

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
