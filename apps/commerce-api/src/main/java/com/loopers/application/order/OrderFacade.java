package com.loopers.application.order;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final UserService userService;
    private final BrandService brandService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public OrderInfo createOrder(String loginId, String rawPassword,
                                 List<OrderRequest.OrderItemRequest> items, Long issuedCouponId) {
        User user = userService.authenticate(loginId, rawPassword);

        // 데드락 방지: 상품 ID 오름차순으로 정렬하여 락 획득 순서를 일관되게 보장
        List<OrderRequest.OrderItemRequest> sortedItems = items.stream()
            .sorted(Comparator.comparingLong(OrderRequest.OrderItemRequest::productId))
            .toList();

        List<Product> products = new ArrayList<>();
        long originalAmount = 0L;
        for (OrderRequest.OrderItemRequest item : sortedItems) {
            Product product = productService.getProduct(item.productId());
            productService.decrementStock(item.productId(), item.quantity());
            originalAmount += (long) product.getPrice() * item.quantity();
            products.add(product);
        }

        // 쿠폰 적용
        long discountAmount = 0L;
        IssuedCoupon issuedCoupon = null;
        if (issuedCouponId != null) {
            issuedCoupon = issuedCouponRepository.findByIdForUpdate(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

            if (!issuedCoupon.getUserId().equals(user.getId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인 소유의 쿠폰이 아닙니다.");
            }

            CouponTemplate template = couponTemplateRepository.findById(issuedCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));

            // 만료 여부는 CouponTemplate 엔티티가 판단 (도메인 규칙)
            if (template.isExpired()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
            }

            discountAmount = template.calculateDiscount(originalAmount);
            try {
                issuedCoupon.use();
            } catch (ObjectOptimisticLockingFailureException e) {
                throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.");
            }
        }

        long finalAmount = originalAmount - discountAmount;
        user.deductBalance(finalAmount);

        // 주문 시점의 브랜드명을 스냅샷으로 저장하기 위해 브랜드 정보를 한 번에 조회 (N+1 방지)
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));

        String orderNumber = orderService.generateOrderNumber();
        Order order = issuedCoupon != null
            ? orderService.createOrder(user.getId(), orderNumber, originalAmount, discountAmount, issuedCoupon.getId())
            : orderService.createOrder(user.getId(), orderNumber, originalAmount);

        // 상품명, 브랜드명, 이미지 URL, 단가를 스냅샷으로 저장 (이후 상품 정보 변경에도 주문 내역 보존)
        for (int i = 0; i < items.size(); i++) {
            Product product = products.get(i);
            OrderRequest.OrderItemRequest item = items.get(i);
            Brand brand = brandMap.get(product.getBrandId());
            orderService.createOrderItem(order.getId(), product.getId(),
                product.getName(), brand.getName(), product.getImageUrl(), product.getPrice(), item.quantity());
        }

        List<OrderItem> orderItems = orderService.getOrderItems(order.getId());
        outboxEventPublisher.publish(
            KafkaTopics.ORDER_EVENTS,
            order.getId().toString(),
            "ORDER_CREATED",
            Map.of("orderId", order.getId(), "userId", order.getUserId(), "totalAmount", order.getTotalAmount())
        );
        eventPublisher.publishEvent(OrderCreatedEvent.from(order));
        return OrderInfo.from(order, orderItems.stream().map(OrderItemInfo::from).toList());
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getOrders(String loginId, String rawPassword, Pageable pageable) {
        User user = userService.authenticate(loginId, rawPassword);
        Page<Order> orders = orderService.getOrders(user.getId(), pageable);

        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        Map<Long, List<OrderItemInfo>> itemsByOrderId = orderService.getOrderItemsByOrderIds(orderIds).stream()
            .collect(Collectors.groupingBy(
                OrderItem::getOrderId,
                Collectors.mapping(OrderItemInfo::from, Collectors.toList())
            ));

        return orders.map(order ->
            OrderInfo.from(order, itemsByOrderId.getOrDefault(order.getId(), List.of()))
        );
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderDetail(String loginId, String rawPassword, Long orderId) {
        User user = userService.authenticate(loginId, rawPassword);
        Order order = orderService.getOrder(orderId);
        if (!order.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.from(order, items.stream().map(OrderItemInfo::from).toList());
    }

    @Transactional
    public OrderInfo cancelOrder(String loginId, String rawPassword, Long orderId) {
        User user = userService.authenticate(loginId, rawPassword);
        Order found = orderService.getOrder(orderId);
        if (!found.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        Order order = orderService.cancelOrder(found);

        // 데드락 방지: 상품 ID 오름차순으로 정렬하여 락 획득 순서를 일관되게 보장
        List<OrderItem> orderItems = orderService.getOrderItems(orderId).stream()
            .sorted(Comparator.comparingLong(OrderItem::getProductId))
            .toList();
        for (OrderItem item : orderItems) {
            productService.incrementStock(item.getProductId(), item.getQuantity());
        }

        user.restoreBalance(order.getTotalAmount());

        // 쿠폰이 사용된 경우, 템플릿 만료 여부를 확인한 후 복구
        // - 만료되지 않은 쿠폰: USED → AVAILABLE로 복구 (재사용 가능)
        // - 만료된 쿠폰: 복구하지 않음 (어차피 사용 불가)
        if (order.getIssuedCouponId() != null) {
            IssuedCoupon issuedCoupon = issuedCouponRepository.findById(order.getIssuedCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
            CouponTemplate template = couponTemplateRepository.findById(issuedCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
            if (!template.isExpired()) {
                try {
                    issuedCoupon.restore();
                } catch (ObjectOptimisticLockingFailureException e) {
                    throw new CoreException(ErrorType.CONFLICT, "쿠폰 복구 중 충돌이 발생했습니다. 다시 시도해주세요.");
                }
            }
        }

        return OrderInfo.from(order, orderItems.stream().map(OrderItemInfo::from).toList());
    }

    @Transactional
    public OrderInfo approveOrder(String loginId, String rawPassword, Long orderId) {
        userService.authenticate(loginId, rawPassword);
        Order order = orderService.approveOrder(orderId);
        List<OrderItem> items = orderService.getOrderItems(orderId);
        return OrderInfo.from(order, items.stream().map(OrderItemInfo::from).toList());
    }

    // ─────────────────────────────────────────────────────────────
    // 가주문 (Pre-Order) — 결제 완료 전까지 Redis에만 저장
    // ─────────────────────────────────────────────────────────────

    /**
     * 가주문 생성: 재고/잔액 차감 없이 유효성 검증 후 Redis에 저장.
     * 결제 완료 콜백(SAGA) 시점에 실제 Order DB 저장 및 차감이 일어난다.
     */
    public PreOrderInfo createPreOrder(String loginId, String rawPassword,
                                       List<OrderRequest.OrderItemRequest> items, Long issuedCouponId) {
        User user = userService.authenticate(loginId, rawPassword);

        // 재고 존재 여부만 확인 (차감 X)
        List<Product> products = new ArrayList<>();
        long originalAmount = 0L;
        for (OrderRequest.OrderItemRequest item : items) {
            Product product = productService.getProduct(item.productId());
            if (product.getStock() < item.quantity()) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "상품 재고가 부족합니다: productId=" + item.productId());
            }
            originalAmount += (long) product.getPrice() * item.quantity();
            products.add(product);
        }

        // 쿠폰 유효성 확인만 (사용 처리 X)
        long discountAmount = 0L;
        if (issuedCouponId != null) {
            IssuedCoupon issuedCoupon = issuedCouponRepository.findById(issuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
            if (!issuedCoupon.getUserId().equals(user.getId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인 소유의 쿠폰이 아닙니다.");
            }
            CouponTemplate template = couponTemplateRepository.findById(issuedCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
            if (template.isExpired()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
            }
            discountAmount = template.calculateDiscount(originalAmount);
        }

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));

        List<PreOrderItem> preOrderItems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Product product = products.get(i);
            Brand brand = brandMap.get(product.getBrandId());
            preOrderItems.add(new PreOrderItem(
                product.getId(), items.get(i).quantity(),
                product.getPrice(), product.getName(),
                brand.getName(), product.getImageUrl()
            ));
        }

        PreOrder preOrder = new PreOrder(
            UUID.randomUUID().toString(),
            user.getId(), preOrderItems,
            issuedCouponId, originalAmount, discountAmount
        );
        preOrderCacheService.save(preOrder);
        return PreOrderInfo.from(preOrder);
    }

    /**
     * 가주문 수정: DRAFT 상태일 때만 가능, TTL 리셋.
     * 결제가 시작된 후(PAYMENT_REQUESTED)에는 수정 불가.
     */
    public PreOrderInfo updatePreOrder(String loginId, String rawPassword, String preOrderId,
                                       List<OrderRequest.OrderItemRequest> newItems, Long newIssuedCouponId) {
        User user = userService.authenticate(loginId, rawPassword);
        PreOrder preOrder = preOrderCacheService.get(preOrderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "가주문을 찾을 수 없거나 만료되었습니다."));

        if (!preOrder.getUserId().equals(user.getId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "가주문을 찾을 수 없습니다.");
        }
        if (!preOrder.canModify()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                preOrder.getStatus() == PreOrderStatus.PAYMENT_REQUESTED
                    ? "결제가 진행 중인 주문은 수정할 수 없습니다."
                    : "만료된 가주문은 수정할 수 없습니다.");
        }

        List<Product> products = new ArrayList<>();
        long originalAmount = 0L;
        for (OrderRequest.OrderItemRequest item : newItems) {
            Product product = productService.getProduct(item.productId());
            if (product.getStock() < item.quantity()) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                    "상품 재고가 부족합니다: productId=" + item.productId());
            }
            originalAmount += (long) product.getPrice() * item.quantity();
            products.add(product);
        }

        long discountAmount = 0L;
        if (newIssuedCouponId != null) {
            IssuedCoupon issuedCoupon = issuedCouponRepository.findById(newIssuedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
            if (!issuedCoupon.getUserId().equals(user.getId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인 소유의 쿠폰이 아닙니다.");
            }
            CouponTemplate template = couponTemplateRepository.findById(issuedCoupon.getCouponTemplateId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
            if (template.isExpired()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
            }
            discountAmount = template.calculateDiscount(originalAmount);
        }

        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));

        List<PreOrderItem> preOrderItems = new ArrayList<>();
        for (int i = 0; i < newItems.size(); i++) {
            Product product = products.get(i);
            Brand brand = brandMap.get(product.getBrandId());
            preOrderItems.add(new PreOrderItem(
                product.getId(), newItems.get(i).quantity(),
                product.getPrice(), product.getName(),
                brand.getName(), product.getImageUrl()
            ));
        }

        preOrder.updateItems(preOrderItems, originalAmount, discountAmount, newIssuedCouponId);
        preOrderCacheService.saveAndResetTtl(preOrder);  // TTL 리셋
        return PreOrderInfo.from(preOrder);
    }
}
