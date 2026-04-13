package com.loopers.application.order;

import com.loopers.application.cart.CartAppService;
import com.loopers.application.coupon.CouponAppService;
import com.loopers.application.product.ProductAppService;
import com.loopers.application.queue.TokenService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.domain.event.OrderCanceledEvent;
import com.loopers.domain.event.OrderCreatedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderAppService {
    private final OrderRepository orderRepository;
    private final ProductAppService productAppService;
    private final CouponAppService couponAppService;
    private final CartAppService cartAppService;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenService tokenService;

    @Transactional
    public Order createOrder(OrderCreateCommand command) {
        // 1. 쿠폰 락 획득
        IssuedCoupon issuedCoupon = null;
        if (command.getCouponId() != null) {
            issuedCoupon = couponAppService.getIssuedCouponWithLock(command.getCouponId(), command.getUserId());
        }

        // 2. 재고 차감 (optionId 오름차순 정렬)
        List<OrderCreateCommand.OrderItemCommand> sortedItems = command.getItems().stream()
                .sorted(Comparator.comparing(OrderCreateCommand.OrderItemCommand::getOptionId))
                .toList();

        List<OrderItem> orderItems = new ArrayList<>();
        List<Long> productIds = new ArrayList<>();
        for (OrderCreateCommand.OrderItemCommand itemCommand : sortedItems) {
            Option option = productAppService.getOptionByIdWithLock(itemCommand.getOptionId());
            option.decreaseStock(itemCommand.getQuantity());

            Product product = productAppService.getById(option.getProductId());
            for (int q = 0; q < itemCommand.getQuantity(); q++) {
                productIds.add(product.getId());
            }
            Money totalPrice = product.getBasePrice().add(option.getAdditionalPrice());

            OrderItem orderItem = OrderItem.of(
                    option.getId(),
                    product.getName(),
                    option.getName(),
                    totalPrice,
                    itemCommand.getQuantity()
            );
            orderItems.add(orderItem);
        }

        // 3. 할인 계산
        Money totalAmount = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(Money.zero(), Money::add);

        Money discountAmount = Money.zero();
        if (issuedCoupon != null) {
            issuedCoupon.validateUsable(totalAmount);
            discountAmount = issuedCoupon.calculateDiscount(totalAmount);
        }

        // 4. 주문 생성
        Long issuedCouponId = issuedCoupon != null ? issuedCoupon.getId() : null;
        Order order = Order.create(command.getUserId(), orderItems, issuedCouponId, discountAmount);
        order = orderRepository.save(order);

        // 5. 쿠폰 사용
        if (issuedCoupon != null) {
            issuedCoupon.use(order.getId());
        }

        // 6. 주문 생성 이벤트 발행
        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getId(), command.getUserId(), productIds, totalAmount.getAmount().longValue(), ZonedDateTime.now()));

        // 7. 입장 토큰 삭제
        tokenService.delete(command.getUserId());

        return order;
    }

    @Transactional
    public Order createOrderFromCart(Long userId, List<Long> cartItemIds, Long couponId) {
        List<CartItem> cartItems = cartAppService.getByIds(cartItemIds);
        for (CartItem cartItem : cartItems) {
            cartItem.validateOwner(userId);
        }

        List<OrderCreateCommand.OrderItemCommand> itemCommands = cartItems.stream()
                .map(ci -> new OrderCreateCommand.OrderItemCommand(ci.getOptionId(), ci.getQuantity()))
                .toList();

        OrderCreateCommand command = new OrderCreateCommand(userId, itemCommands, couponId);
        Order order = createOrder(command);

        cartAppService.deleteByIds(cartItemIds);
        return order;
    }

    @Transactional
    public Order cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.validateOwner(userId);
        return cancelOrderInternal(order);
    }

    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> getByUserId(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        orders.forEach(order -> order.getOrderItems().size());
        return orders;
    }

    @Transactional
    public Order pay(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.pay();
        return order;
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        return cancelOrderInternal(order);
    }

    private Order cancelOrderInternal(Order order) {
        // 1. 취소 가능 여부 먼저 검증 (Fail-Fast: 불필요한 Lock 획득 방지)
        order.cancel();

        // 2. IssuedCoupon 락 획득
        IssuedCoupon issuedCoupon = null;
        if (order.getIssuedCouponId() != null) {
            issuedCoupon = couponAppService.getIssuedCouponByIdWithLock(order.getIssuedCouponId());
        }

        // 3. Option 락 획득 (ID 오름차순)
        List<OrderItem> sortedItems = order.getOrderItems().stream()
                .sorted(Comparator.comparing(OrderItem::getOptionId))
                .toList();

        List<Option> lockedOptions = new ArrayList<>();
        for (OrderItem item : sortedItems) {
            Option option = productAppService.getOptionByIdWithLock(item.getOptionId());
            lockedOptions.add(option);
        }

        // 4. 쿠폰 복원
        if (issuedCoupon != null) {
            issuedCoupon.restore();
        }

        // 5. 재고 복원
        for (int i = 0; i < sortedItems.size(); i++) {
            lockedOptions.get(i).increaseStock(sortedItems.get(i).getQuantity());
        }

        // 6. 주문 취소 이벤트 발행
        List<Long> productIds = new ArrayList<>();
        for (int i = 0; i < sortedItems.size(); i++) {
            for (int q = 0; q < sortedItems.get(i).getQuantity(); q++) {
                productIds.add(lockedOptions.get(i).getProductId());
            }
        }
        long totalAmount = order.getTotalAmount().getAmount().longValue();
        eventPublisher.publishEvent(new OrderCanceledEvent(
                order.getId(), order.getUserId(), productIds, totalAmount, ZonedDateTime.now()));

        return order;
    }

    @Transactional
    public Order prepare(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.prepare();
        return order;
    }

    @Transactional
    public Order ship(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.ship();
        return order;
    }

    @Transactional
    public Order deliver(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.deliver();
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getAll() {
        List<Order> orders = orderRepository.findAll();
        orders.forEach(order -> order.getOrderItems().size());
        return orders;
    }

    @Transactional(readOnly = true)
    public Page<Order> getAll(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);
        orders.forEach(order -> order.getOrderItems().size());
        return orders;
    }
}
