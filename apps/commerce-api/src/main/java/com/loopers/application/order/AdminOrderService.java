package com.loopers.application.order;

import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.dto.OrderCommand;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.dto.ProductInfo;
import com.loopers.domain.user.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final ProductService productService;
    private final BrandService brandService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final UserService userService;

    @Transactional
    public OrderResult.OrderSummary createOrder(Long userId, OrderCriteria.Create criteria) {
        List<ProductInfo.StockDeduction> deductionInfos =
                productService.validateAndDeductStock(criteria.toStockDeductions());

        Map<Long, String> brandNameMap = brandService.getNameMapByIds(
                ProductInfo.StockDeduction.extractDistinctBrandIds(deductionInfos));

        OrderModel order = orderService.createOrder(
                userId, OrderCommand.CreateItem.from(deductionInfos, brandNameMap));

        if (criteria.ownedCouponId() != null) {
            orderService.applyDiscount(
                    order,
                    (int) couponService.useAndCalculateDiscount(
                            criteria.ownedCouponId(), userId, order.getId(),
                            order.getOriginalTotalPrice()));
        }

        userService.deductPoint(userId, order.getTotalPrice());

        return OrderResult.OrderSummary.from(order);
    }
}
