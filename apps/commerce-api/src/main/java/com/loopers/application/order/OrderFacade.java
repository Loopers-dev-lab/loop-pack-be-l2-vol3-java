package com.loopers.application.order;

import com.loopers.application.cart.CartAppService;
import com.loopers.application.product.ProductAppService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {
    private final OrderAppService orderAppService;
    private final ProductAppService productAppService;
    private final CartAppService cartAppService;
    private final MemberRepository memberRepository;
    private final IssuedCouponRepository issuedCouponRepository;
    private final OptionRepository optionRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(OrderCreateCommand command) {
        // 1. Member 락 (포인트 차감)
        Member member = memberRepository.findByIdWithLock(command.getUserId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        // 2. 쿠폰 확인 (IssuedCoupon의 스냅샷에서 할인 정보 사용)
        IssuedCoupon issuedCoupon = null;
        if (command.getCouponId() != null) {
            issuedCoupon = issuedCouponRepository.findByCouponIdAndUserIdWithLock(command.getCouponId(), command.getUserId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
        }

        // 3. 재고 차감 (optionId 오름차순 정렬)
        List<OrderCreateCommand.OrderItemCommand> sortedItems = command.getItems().stream()
                .sorted(Comparator.comparing(OrderCreateCommand.OrderItemCommand::getOptionId))
                .toList();

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderCreateCommand.OrderItemCommand itemCommand : sortedItems) {
            Option option = optionRepository.findByIdWithLock(itemCommand.getOptionId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "옵션을 찾을 수 없습니다."));
            option.decreaseStock(itemCommand.getQuantity());

            Product product = productAppService.getById(option.getProductId());
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

        // 4. 할인 계산
        Money totalAmount = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(Money.zero(), Money::add);

        Money discountAmount = Money.zero();
        if (issuedCoupon != null) {
            issuedCoupon.validateUsable(totalAmount);
            discountAmount = issuedCoupon.calculateDiscount(totalAmount);
        }

        // 5. 포인트 차감
        Money paymentAmount = totalAmount.subtract(discountAmount);
        member.deductPoint(paymentAmount);

        // 6. 주문 생성
        Long issuedCouponId = issuedCoupon != null ? issuedCoupon.getId() : null;
        Order order = Order.create(command.getUserId(), orderItems, issuedCouponId, discountAmount);
        order = orderAppService.create(order);

        // 7. 쿠폰 사용
        if (issuedCoupon != null) {
            issuedCoupon.use(order.getId());
        }

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
        // 0. Order 락 획득 (동시 취소 방지)
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.validateOwner(userId);

        // 1. Member 락 획득
        Member member = memberRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        // 2. IssuedCoupon 락 획득
        IssuedCoupon issuedCoupon = null;
        if (order.getIssuedCouponId() != null) {
            issuedCoupon = issuedCouponRepository.findByIdWithLock(order.getIssuedCouponId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급된 쿠폰을 찾을 수 없습니다."));
        }

        // 3. Option 락 획득 (ID 오름차순)
        List<OrderItem> sortedItems = order.getOrderItems().stream()
                .sorted(Comparator.comparing(OrderItem::getOptionId))
                .toList();

        List<Option> lockedOptions = new ArrayList<>();
        for (OrderItem item : sortedItems) {
            Option option = optionRepository.findByIdWithLock(item.getOptionId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "옵션을 찾을 수 없습니다."));
            lockedOptions.add(option);
        }

        // 락 획득 완료 — 복구 로직 수행

        // 포인트 복원
        member.addPoint(order.getPaymentAmount());

        // 쿠폰 복원
        if (issuedCoupon != null) {
            issuedCoupon.restore();
        }

        // 재고 복원
        for (int i = 0; i < sortedItems.size(); i++) {
            lockedOptions.get(i).increaseStock(sortedItems.get(i).getQuantity());
        }

        order.cancel();
        return order;
    }

    public Order getOrder(Long userId, Long orderId) {
        Order order = orderAppService.getById(orderId);
        order.validateOwner(userId);
        return order;
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderAppService.getByUserId(userId);
    }

    public List<Order> getAll() {
        return orderAppService.getAll();
    }

    public Page<Order> getAll(Pageable pageable) {
        return orderAppService.getAll(pageable);
    }

    public Order getById(Long orderId) {
        return orderAppService.getById(orderId);
    }

    public Order payOrder(Long orderId) {
        return orderAppService.pay(orderId);
    }

    public Order prepareOrder(Long orderId) {
        return orderAppService.prepare(orderId);
    }

    public Order shipOrder(Long orderId) {
        return orderAppService.ship(orderId);
    }

    public Order deliverOrder(Long orderId) {
        return orderAppService.deliver(orderId);
    }
}
