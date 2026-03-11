package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderItemService {

    private final OrderItemRepository orderItemRepository;

    public void createOrderItems(Long orderId, List<Product> products, Map<Long, Integer> productQuantities) {
        if (products == null || products.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_ITEMS_EMPTY);
        }
        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_QUANTITIES_EMPTY);
        }

        List<OrderItem> orderItems = products.stream()
            .map(product -> OrderItem.create(
                null,
                orderId,
                product.getId(),
                productQuantities.get(product.getId()),
                product.getPrice().value()
            ))
            .toList();

        orderItemRepository.saveAll(orderItems);
    }

    public Money calculateTotalPrice(List<Product> products, Map<Long, Integer> productQuantities) {
        return products.stream()
            .map(product -> product.getPrice().multiply(productQuantities.get(product.getId())))
            .reduce(Money.ZERO, Money::add);
    }
}
