package com.loopers.application.order;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.point.PointService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.vo.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderService orderService;
    private final MemberService memberService;
    private final ProductService productService;
    private final BrandService brandService;
    private final PointService pointService;
    private final CouponService couponService;
    private final UserCouponService userCouponService;
    private final ApplicationEventPublisher eventPublisher;
    private final EntryTokenService entryTokenService;

    @Transactional
    public OrderInfo createOrder(String loginId, String password,
                                 List<OrderItemRequest> itemRequests, Long userCouponId) {
        // 1. 회원 인증
        MemberModel member = memberService.getMyInfo(loginId, password);

        // 2. 입장 토큰 검증
        entryTokenService.validateAndDelete(member.getId());

        // 3. 주문 항목 생성 + 재고 차감 (비관적 락)
        List<OrderItemModel> orderItems = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            ProductModel product = productService.getById(req.productId());
            productService.decreaseStockWithLock(product.getId(), req.quantity());

            OrderItemModel item = new OrderItemModel(
                    product.getId(),
                    product.getName(),
                    brandService.getById(product.getBrandId()).getName(),
                    product.getPrice(),
                    req.quantity()
            );
            orderItems.add(item);
        }

        // 4. 쿠폰 처리
        int discountAmount = 0;
        if (userCouponId != null) {
            // 4-1. 발급 쿠폰 사용 처리 (소유자 검증 + AVAILABLE→USED)
            UserCouponModel userCoupon = userCouponService.use(userCouponId, member.getId());

            // 4-2. 쿠폰 템플릿 조회
            CouponModel coupon = couponService.getById(userCoupon.getCouponId());

            // 4-3. 만료 여부 검증
            coupon.validateNotExpired();

            // 4-4. 주문 금액 계산 (할인 전)
            int originalAmount = orderItems.stream()
                    .mapToInt(OrderItemModel::getTotalAmount)
                    .sum();

            // 4-5. 최소 주문금액 검증
            coupon.validateMinOrderAmount(Money.of(originalAmount));

            // 4-6. 할인 금액 계산
            Money discount = coupon.calculateDiscount(Money.of(originalAmount));
            discountAmount = discount.amount().intValue();
        }

        // 5. 주문 생성 (할인 스냅샷 포함)
        OrderModel order = orderService.createOrder(
                member.getId(), orderItems, userCouponId, discountAmount);

        // 6. 포인트 차감 - 돈이 걸린 핵심 로직이므로 같은 TX에 유지
        pointService.use(member.getId(), Money.of(order.getTotalAmount()));
        List<OrderItemModel> savedItems = orderService.getOrderItems(order.getId());

        // 7. 이벤트 발행 - TX 커밋 후 비동기로 부가 처리 (로깅, 향후 Kafka 발행)
        eventPublisher.publishEvent(OrderCreatedEvent.from(order, savedItems));

        return OrderInfo.from(order, savedItems);
    }

    @Transactional(readOnly = true)
    public OrderInfo getById(Long id) {
        OrderModel order = orderService.getById(id);
        List<OrderItemModel> items = orderService.getOrderItems(order.getId());
        return OrderInfo.from(order, items);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getByMember(String loginId, String password) {
        MemberModel member = memberService.getMyInfo(loginId, password);
        List<OrderModel> orders = orderService.getByMemberId(member.getId());

        return orders.stream()
                .map(order -> {
                    List<OrderItemModel> items = orderService.getOrderItems(order.getId());
                    return OrderInfo.from(order, items);
                })
                .toList();
    }

    public record OrderItemRequest(
            Long productId,
            int quantity
    ) {
    }
}
