package com.loopers.application.order;

import com.loopers.domain.model.common.DomainEventPublisher;
import com.loopers.domain.model.order.*;
import com.loopers.domain.model.product.Product;
import com.loopers.domain.model.user.UserId;
import com.loopers.domain.repository.OrderRepository;
import com.loopers.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OrderService implements CreateOrderUseCase, CancelOrderUseCase, UpdateDeliveryAddressUseCase {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DomainEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        DomainEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void createOrder(UserId userId, OrderCommand command) {
        List<OrderLine> orderLines = command.items().stream()
                .map(itemCommand -> {
                    Product product = productRepository.findActiveByIdWithLock(itemCommand.productId())
                            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. ID: " + itemCommand.productId()));

                    Product decreased = product.decreaseStock(itemCommand.quantity());
                    productRepository.save(decreased);

                    return new OrderLine(
                            product.getId(),
                            product.getName().getValue(),
                            Money.of(product.getPrice().getValue()),
                            itemCommand.quantity()
                    );
                })
                .toList();

        DeliveryInfo deliveryInfo = DeliveryInfo.of(
                command.receiverName(),
                command.address(),
                command.deliveryRequest(),
                command.desiredDeliveryDate()
        );

        PaymentMethod paymentMethod = PaymentMethod.valueOf(command.paymentMethod());
        Order order = Order.create(userId, orderLines, deliveryInfo, paymentMethod, Money.zero());

        orderRepository.save(order);
    }

    @Override
    public void cancelOrder(UserId userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        Order cancelled = order.cancel();
        orderRepository.save(cancelled);
        eventPublisher.publishEvents(cancelled);
    }

    @Override
    public void updateDeliveryAddress(UserId userId, Long orderId, String newAddress) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));

        Order updated = order.updateDeliveryAddress(newAddress);
        orderRepository.save(updated);
    }
}
