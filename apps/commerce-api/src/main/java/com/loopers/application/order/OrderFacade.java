package com.loopers.application.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.product.ProductStockApplicationService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderApplicationService orderApplicationService;
    private final ProductStockApplicationService productStockApplicationService;
    private final BrandApplicationService brandApplicationService;

    public Order create(CreateOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<ProductStockApplicationService.ReservedProduct> reservedProducts =
                productStockApplicationService.reserveForOrder(command.items());

        Map<UUID, String> brandNames = reservedProducts.stream()
                .map(ProductStockApplicationService.ReservedProduct::brandId)
                .distinct()
                .collect(Collectors.toMap(
                        brandId -> brandId,
                        brandId -> brandApplicationService.findById(brandId).name().value()
                ));

        List<OrderItem> orderItems = reservedProducts.stream()
                .map(reserved -> new OrderItem(
                        reserved.productId(),
                        reserved.quantity(),
                        reserved.productName(),
                        reserved.productPrice(),
                        brandNames.get(reserved.brandId())
                ))
                .toList();

        return orderApplicationService.create(command.userId(), orderItems);
    }

    public Order cancel(OrderAccessRequest request) {
        Order cancelled = orderApplicationService.cancel(request);
        productStockApplicationService.restoreForOrder(cancelled.items());
        return cancelled;
    }
}
