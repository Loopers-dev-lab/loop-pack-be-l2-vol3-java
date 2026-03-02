package com.loopers.application.order;

import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemService;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.OrderErrorType;
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
 * Order + Address + Product + Brand + Inventory + Cart 도메인 서비스를 조합하여
 * 주문 생성, 장바구니 기반 주문 생성, 주문 취소를 처리한다.
 */
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final UserAddressService userAddressService;
    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;
    private final CartItemService cartItemService;

    public OrderFacade(OrderService orderService, UserAddressService userAddressService,
                       ProductService productService, BrandService brandService,
                       InventoryService inventoryService, CartItemService cartItemService) {
        this.orderService = orderService;
        this.userAddressService = userAddressService;
        this.productService = productService;
        this.brandService = brandService;
        this.inventoryService = inventoryService;
        this.cartItemService = cartItemService;
    }

    /**
     * 주문 생성 (F-11)
     *
     * 1. items 유효성 검증 (비어있으면 400)
     * 2. 배송지 조회 + 소유권 검증
     * 3. 각 상품 존재/판매가능 확인 (ACTIVE만 주문 가능)
     * 4. 재고 예약 (비관적 락, 원자적 수행)
     * 5. Order(PENDING) + OrderItem 스냅샷 생성
     */
    @Transactional
    public OrderCreateResult createOrder(Long userId, String userName, String ordererPhone,
                                          List<OrderItemCommand> itemCommands, Long addressId) {
        Order order = doCreateOrder(userId, userName, ordererPhone, itemCommands, addressId);
        return new OrderCreateResult(
                order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getExpiresAt());
    }

    /**
     * 장바구니 기반 주문 생성
     *
     * 1. 장바구니 아이템 조회 + 소유권 검증
     * 2. CartItem → OrderItemCommand 변환
     * 3. 기존 주문 생성 로직 재사용 (상품 검증 → 재고 예약 → Order 생성)
     * 4. 주문 성공 시 장바구니 아이템 소프트 삭제
     */
    @Transactional
    public OrderCreateResult createOrderFromCart(Long userId, String userName, String ordererPhone,
                                                  List<Long> cartItemIds, Long addressId) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new CoreException(OrderErrorType.EMPTY_ORDER_ITEMS);
        }

        List<CartItem> cartItems = cartItemService.getCartItemsByIds(cartItemIds, userId);

        List<OrderItemCommand> itemCommands = cartItems.stream()
                .map(item -> new OrderItemCommand(item.getProductId(), item.getQuantity()))
                .toList();

        Order order = doCreateOrder(userId, userName, ordererPhone, itemCommands, addressId);

        cartItemService.deleteAll(cartItemIds, userId);

        return new OrderCreateResult(
                order.getId(), order.getOrderNumber(), order.getStatus().name(), order.getExpiresAt());
    }

    /**
     * 주문 생성 내부 로직 (공통)
     */
    private Order doCreateOrder(Long userId, String userName, String ordererPhone,
                                List<OrderItemCommand> itemCommands, Long addressId) {
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(OrderErrorType.EMPTY_ORDER_ITEMS);
        }

        UserAddress address = userAddressService.getAddress(addressId, userId);

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

        inventoryService.reserveAll(productQtyMap);

        String orderNumber = generateOrderNumber();

        return orderService.create(
                userId, orderNumber, orderItems,
                userName, ordererPhone,
                address.getReceiverName(), address.getPhone(),
                address.getZipCode(), address.getAddressLine1(), address.getAddressLine2()
        );
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
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderService.cancel(orderId, userId);

        Map<Long, Integer> productQtyMap = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity, Integer::sum));
        inventoryService.releaseAll(productQtyMap);
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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

    public record OrderCreateResult(
            Long orderId, String orderNumber, String status, ZonedDateTime expiresAt) {}

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
