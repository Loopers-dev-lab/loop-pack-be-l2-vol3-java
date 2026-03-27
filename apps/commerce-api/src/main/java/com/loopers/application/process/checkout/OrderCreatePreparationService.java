package com.loopers.application.process.checkout;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.product.ProductStockApplicationService;
import com.loopers.application.product.dto.OrderProductInfo;
import com.loopers.domain.order.OrderItem;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCreatePreparationService {

    private final ProductStockApplicationService productStockApplicationService;
    private final BrandApplicationService brandApplicationService;
    private final CouponApplicationService couponApplicationService;

    public PreparedOrder prepare(CreateOrderCommand command) {
        List<OrderProductInfo> orderProducts = productStockApplicationService.getOrderProducts(command.items());

        List<UUID> brandIds = orderProducts.stream().map(OrderProductInfo::brandId).toList();
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(brandIds);
        Map<UUID, OrderProductInfo> orderProductMap = orderProducts.stream()
                .collect(Collectors.toMap(OrderProductInfo::productId, p -> p));

        List<OrderItem> orderItems = command.items().stream()
                .map(item -> {
                    var p = orderProductMap.get(item.productId());
                    return new OrderItem(p.productId(), item.quantity(), p.productName(), p.productPrice(), brandNames.get(p.brandId()));
                })
                .toList();

        int orderAmount = orderItems.stream().mapToInt(OrderItem::totalPrice).sum();
        int discountAmount = 0;
        if (command.couponId() != null) {
            discountAmount = couponApplicationService.calculateDiscount(command.couponId(), orderAmount);
        }

        int amountAfterCoupon = Math.max(orderAmount - discountAmount, 0);
        if (command.pointAmount() > amountAfterCoupon) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 포인트가 결제 예정 금액을 초과할 수 없습니다.");
        }
        int paymentAmount = amountAfterCoupon - command.pointAmount();
        return new PreparedOrder(orderItems, orderAmount, paymentAmount, command.pointAmount());
    }

    public record PreparedOrder(List<OrderItem> orderItems, int orderAmount, int paymentAmount, int usedPointAmount) {
    }
}
