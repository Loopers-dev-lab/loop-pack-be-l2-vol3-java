package com.loopers.application.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;

    @Transactional
    public OrderResult placeOrder(Long userId, List<OrderItemCommand> commands) {
        Money totalAmount = Money.ZERO;
        List<SnapshotHolder> snapshots = new ArrayList<>();

        for (OrderItemCommand cmd : commands) {
            ProductModel product = productService.getProduct(cmd.productId());

            StockModel stock = stockService.getByProductId(cmd.productId());
            stock.decrease(cmd.quantity());

            Money subtotal = product.price().multiply(cmd.quantity());
            totalAmount = totalAmount.add(subtotal);

            snapshots.add(new SnapshotHolder(
                product.getId(), product.name(), product.price(), cmd.quantity()
            ));
        }

        OrderModel order = orderService.save(new OrderModel(userId, totalAmount));

        List<OrderItemModel> items = snapshots.stream()
            .map(s -> new OrderItemModel(
                order.getId(), s.productId(), s.productName(), s.productPrice(), s.quantity()
            ))
            .toList();

        List<OrderItemModel> savedItems = orderService.saveAllItems(items);

        return OrderResult.of(order, savedItems);
    }

    private record SnapshotHolder(
        Long productId, String productName, Money productPrice, int quantity
    ) {}
}
