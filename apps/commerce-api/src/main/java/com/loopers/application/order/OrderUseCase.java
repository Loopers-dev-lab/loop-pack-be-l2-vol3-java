package com.loopers.application.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.command.UseCouponCommand;
import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.product.ProductStockApplicationService;
import com.loopers.application.product.dto.OrderProductInfo;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderUseCase {

    private final OrderApplicationService orderApplicationService;
    private final ProductStockApplicationService productStockApplicationService;
    private final BrandApplicationService brandApplicationService;
    private final CouponApplicationService couponApplicationService;

    @Transactional
    public Order create(CreateOrderCommand command) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderProductInfo> orderProducts =
                productStockApplicationService.getOrderProducts(command.items());

        List<UUID> brandIds = orderProducts.stream()
                .map(OrderProductInfo::brandId)
                .toList();
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(brandIds);

        Map<UUID, OrderProductInfo> orderProductMap = orderProducts.stream()
                .collect(Collectors.toMap(OrderProductInfo::productId, p -> p));

        List<OrderItem> orderItems = command.items().stream()
                .map(item -> {
                    var p = orderProductMap.get(item.productId());
                    return new OrderItem(
                            p.productId(),
                            item.quantity(),
                            p.productName(),
                            p.productPrice(),
                            brandNames.get(p.brandId())
                    );
                })
                .toList();

        if (command.couponId() != null) {
            int orderAmount = orderItems.stream().mapToInt(OrderItem::totalPrice).sum();
            couponApplicationService.use(new UseCouponCommand(command.couponId(), command.memberId(), orderAmount));
        }

        Order createdOrder = orderApplicationService.create(command.memberId(), orderItems, command.couponId());
        productStockApplicationService.decreaseStockForOrder(command.items());
        return createdOrder;
    }

    @Transactional
    public Order cancel(OrderAccessRequest request) {
        Order cancelled = orderApplicationService.cancel(request);
        productStockApplicationService.restoreForOrder(cancelled.items());
        if (cancelled.couponId() != null) {
            couponApplicationService.cancelUse(cancelled.couponId(), cancelled.memberId());
        }
        return cancelled;
    }
}
