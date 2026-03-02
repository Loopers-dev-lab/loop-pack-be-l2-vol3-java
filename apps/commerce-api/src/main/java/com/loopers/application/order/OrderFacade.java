package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final UserService userService;

    @Transactional
    public OrderDetailInfo placeOrder(String loginId, String password, List<PlaceOrderItem> items) {
        UserModel user = userService.getMyInfo(loginId, password);

        List<OrderItemModel> orderItems = items.stream()
            .map(item -> {
                ProductModel product = productService.getProduct(item.productId());
                productService.deductStock(item.productId(), item.quantity());
                return new OrderItemModel(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    item.quantity()
                );
            })
            .toList();

        OrderModel order = orderService.placeOrder(user.getId(), orderItems);
        return OrderDetailInfo.from(order);
    }

    public List<OrderSummaryInfo> getMyOrders(String loginId, String password, LocalDate startAt, LocalDate endAt) {
        UserModel user = userService.getMyInfo(loginId, password);
        return orderService.getMyOrders(user.getId(), startAt, endAt)
            .stream()
            .map(OrderSummaryInfo::from)
            .toList();
    }

    public OrderDetailInfo getMyOrder(String loginId, String password, Long orderId) {
        UserModel user = userService.getMyInfo(loginId, password);
        OrderModel order = orderService.getMyOrder(user.getId(), orderId);
        return OrderDetailInfo.from(order);
    }

    public Page<OrderSummaryInfo> getAll(Pageable pageable) {
        return orderService.getAll(pageable).map(OrderSummaryInfo::from);
    }

    public OrderDetailInfo getOrder(Long orderId) {
        OrderModel order = orderService.getOrder(orderId);
        return OrderDetailInfo.from(order);
    }

    public record PlaceOrderItem(Long productId, int quantity) {}
}
