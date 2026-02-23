package com.loopers.application.order;

import com.loopers.application.cart.CartAppService;
import com.loopers.application.product.ProductAppService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {
    private final OrderAppService orderAppService;
    private final ProductAppService productAppService;
    private final CartAppService cartAppService;

    public Order createOrder(OrderCreateCommand command) {
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderCreateCommand.OrderItemCommand itemCommand : command.getItems()) {
            Option option = productAppService.decreaseStock(
                    itemCommand.getOptionId(),
                    itemCommand.getQuantity()
            );

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

        return orderAppService.create(command.getUserId(), orderItems);
    }

    @Transactional
    public Order createOrderFromCart(Long userId, List<Long> cartItemIds) {
        List<CartItem> cartItems = cartAppService.getByIds(cartItemIds);

        for (CartItem cartItem : cartItems) {
            if (!cartItem.getUserId().equals(userId)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인의 장바구니 항목만 주문할 수 있습니다.");
            }
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            Option option = productAppService.decreaseStock(
                    cartItem.getOptionId(),
                    cartItem.getQuantity()
            );

            Product product = productAppService.getById(option.getProductId());
            Money totalPrice = product.getBasePrice().add(option.getAdditionalPrice());

            OrderItem orderItem = OrderItem.of(
                    option.getId(),
                    product.getName(),
                    option.getName(),
                    totalPrice,
                    cartItem.getQuantity()
            );
            orderItems.add(orderItem);
        }

        Order order = orderAppService.create(userId, orderItems);

        cartAppService.deleteByIds(cartItemIds);

        return order;
    }

    @Transactional
    public Order cancelOrder(Long userId, Long orderId) {
        Order order = orderAppService.getById(orderId);
        validateOwnership(order, userId);

        for (OrderItem item : order.getOrderItems()) {
            productAppService.increaseStock(item.getOptionId(), item.getQuantity());
        }

        return orderAppService.cancel(orderId);
    }

    public Order getOrder(Long userId, Long orderId) {
        Order order = orderAppService.getById(orderId);
        validateOwnership(order, userId);
        return order;
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderAppService.getByUserId(userId);
    }

    private void validateOwnership(Order order, Long userId) {
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 조회/취소할 수 있습니다.");
        }
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
