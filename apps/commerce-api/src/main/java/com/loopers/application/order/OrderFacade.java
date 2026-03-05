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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
 * 도메인 서비스를 조합하여 1단계 트랜잭션으로 주문 생성~결제 확정을 처리한다.
 *
 * 주문 흐름 (단일 @Transactional):
 * 1. 쿠폰 검증 + 할인 계산 (읽기)
 * 2. 포인트 잔액 검증 (읽기)
 * 3. 재고 예약 (비관적 락)
 * 4. 주문 생성 (할인 적용)
 * 5. 쿠폰 사용 (낙관적 락 — B-1에서 적용)
 * 6. 포인트 차감 (비관적 락 — B-1에서 적용)
 * 7. PG 결제 시뮬레이션 → Payment 생성/승인
 * 8. Order → PAID 확정 + 포인트 적립
 */
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final UserAddressService userAddressService;
    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;
    private final CartItemService cartItemService;
    private final CouponService couponService;
    private final PointService pointService;
    private final PaymentService paymentService;

    public OrderFacade(OrderService orderService, UserAddressService userAddressService,
                       ProductService productService, BrandService brandService,
                       InventoryService inventoryService, CartItemService cartItemService,
                       CouponService couponService, PointService pointService,
                       PaymentService paymentService) {
        this.orderService = orderService;
        this.userAddressService = userAddressService;
        this.productService = productService;
        this.brandService = brandService;
        this.inventoryService = inventoryService;
        this.cartItemService = cartItemService;
        this.couponService = couponService;
        this.pointService = pointService;
        this.paymentService = paymentService;
    }

    /**
     * 주문 생성 + 결제 확정 (1단계 트랜잭션)
     */
    @Transactional(timeout = 30)
    public OrderCreateResult createOrder(Long userId, String userName, String ordererPhone,
                                          List<OrderItemCommand> itemCommands, Long addressId,
                                          Long issuedCouponId, int pointAmount, String paymentMethod) {
        Order order = doCreateOrder(userId, userName, ordererPhone, itemCommands, addressId,
                issuedCouponId, pointAmount, paymentMethod);
        return new OrderCreateResult(
                order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getTotalAmount(), order.getPaymentId());
    }

    /**
     * 장바구니 기반 주문 생성 + 결제 확정 (1단계 트랜잭션)
     */
    @Transactional(timeout = 30)
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

        Order order = doCreateOrder(userId, userName, ordererPhone, itemCommands, addressId,
                issuedCouponId, pointAmount, paymentMethod);

        cartItemService.deleteAll(cartItemIds, userId);

        return new OrderCreateResult(
                order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getTotalAmount(), order.getPaymentId());
    }

    /**
     * 주문 생성 + 결제 확정 내부 로직 (공통)
     *
     * 락 순서: 재고(비관적) → 쿠폰(낙관적) → 포인트(비관적)
     * - 쿠폰/포인트 검증은 락 이전에 읽기로 수행
     * - 재고 예약이 가장 먼저 (가장 경합이 높은 자원)
     */
    private Order doCreateOrder(Long userId, String userName, String ordererPhone,
                                List<OrderItemCommand> itemCommands, Long addressId,
                                Long issuedCouponId, int pointAmount, String paymentMethod) {
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

            orderItems.add(OrderItem.create(
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

        // 5. 쿠폰 사용 처리 (B-1에서 낙관적 락 적용 예정)
        if (issuedCouponId != null) {
            couponService.use(issuedCouponId, userId, order.getId());
        }

        // 6. 포인트 차감 (B-1에서 비관적 락 적용 예정)
        if (pointAmount > 0) {
            pointService.use(userId, pointAmount);
        }

        // 7. PG 결제 시뮬레이션 + Payment 생성/승인
        String idempotencyKey = generateIdempotencyKey();
        Payment payment = paymentService.create(
                order.getId(), order.getTotalAmount(), paymentMethod, idempotencyKey);

        String pgTxnId = "PG-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        paymentService.approve(payment.getId(), pgTxnId, order.getTotalAmount());

        // 8. 재고 확정 + Order → PAID + 포인트 적립
        inventoryService.commitAll(productQtyMap);
        orderService.confirm(order.getId(), payment.getId(), paymentMethod);
        pointService.earn(userId, order.getTotalAmount());

        return orderService.getById(order.getId());
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
