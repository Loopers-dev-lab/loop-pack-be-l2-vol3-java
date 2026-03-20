package com.loopers.application.order;

import com.loopers.application.order.dto.CreateOrderReqDto;
import com.loopers.application.order.dto.FindOrderResDto;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final OrderProductService orderProductService;
    private final MemberService memberService;
    private final ProductService productService;
    private final CouponService couponService;

    @Transactional(rollbackFor = {Exception.class})
    public FindOrderResDto createOrder(String loginId, String password, CreateOrderReqDto dto) {
        Member member = memberService.findMember(loginId, password);

        // 1. 상품별 재고 차감 (Atomic UPDATE, ID 오름차순 정렬로 데드락 방지)
        List<OrderProduct> orderProducts = dto.items().stream()
                .sorted(Comparator.comparing(CreateOrderReqDto.OrderItemReqDto::productId))
                .map(item -> {
                    Product product = productService.decreaseStockAtomic(item.productId(), item.quantity());
                    return OrderProduct.create(
                            product.getId(),
                            product.getName().value(),
                            product.getPrice().value(),
                            item.quantity()
                    );
                })
                .toList();

        // 2. 주문 상품 합계 계산
        int subtotal = orderProducts.stream()
                .mapToInt(op -> op.getPrice().value() * op.getQuantity().value())
                .sum();

        // 3. 쿠폰 유효성 검증 및 사용 처리 (낙관적 락)
        int discountAmount = 0;
        Long userCouponId = dto.userCouponId();
        if (userCouponId != null) {
            CouponTemplate template = couponService.useUserCoupon(userCouponId, member.getId(), subtotal);
            discountAmount = template.calculateDiscount(subtotal);
        }

        // 4. 주문 생성 및 저장
        OrderCommand.Create command = new OrderCommand.Create(member.getId(), orderProducts, discountAmount, userCouponId);
        Orders savedOrder = orderService.createOrder(command);

        List<OrderProduct> savedProducts = orderProductService.saveAll(savedOrder.getId(), orderProducts);
        Orders result = Orders.reconstruct(
                savedOrder.getId(), savedOrder.getOrderNumber(), savedOrder.getMemberId(),
                savedOrder.getTotalPrice().value(), savedOrder.getDiscountAmount().value(),
                savedOrder.getUserCouponId(), savedOrder.getStatus(), savedProducts
        );
        return FindOrderResDto.from(result);
    }

    public List<FindOrderResDto> getOrders(String loginId, String password, LocalDateTime startAt, LocalDateTime endAt) {
        Member member = memberService.findMember(loginId, password);
        OrderCommand.GetByPeriod command = new OrderCommand.GetByPeriod(member.getId(), startAt, endAt);
        return orderService.getOrders(command).stream()
                .map(this::populateAndConvert)
                .toList();
    }

    public FindOrderResDto getOrder(String loginId, String password, Long orderId) {
        Member member = memberService.findMember(loginId, password);
        OrderCommand.GetByMember command = new OrderCommand.GetByMember(member.getId(), orderId);
        Orders orders = orderService.getOrder(command);
        return populateAndConvert(orders);
    }

    private FindOrderResDto populateAndConvert(Orders orders) {
        List<OrderProduct> orderProducts = orderProductService.findByOrderId(orders.getId());
        Orders populated = Orders.reconstruct(
                orders.getId(), orders.getOrderNumber(), orders.getMemberId(),
                orders.getTotalPrice().value(), orders.getDiscountAmount().value(),
                orders.getUserCouponId(), orders.getStatus(), orderProducts
        );
        return FindOrderResDto.from(populated);
    }
}
