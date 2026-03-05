package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    @Transactional
    public Order createOrder(Long memberId, List<OrderItemRequest> itemRequests) {
        return createOrder(memberId, itemRequests, null);
    }

    @Transactional
    public Order createOrder(Long memberId, List<OrderItemRequest> itemRequests, Long couponIssueId) {
        // 1. 상품 조회(비관적 락) + 재고 차감
        List<Product> products = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            Product product = productRepository.findByIdWithLock(req.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
            product.decreaseStock(req.quantity());
            products.add(product);
        }

        // 2. 브랜드 한 번에 조회 (N+1 방지)
        Set<Long> brandIds = products.stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandRepository.findAllByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Function.identity()));

        // 3. 스냅샷 생성
        List<Order.ItemSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < itemRequests.size(); i++) {
            Product product = products.get(i);
            Brand brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : null;

            snapshots.add(new Order.ItemSnapshot(
                product.getId(),
                product.getName(),
                product.getPrice().getValue(),
                brandName,
                itemRequests.get(i).quantity()
            ));
        }

        // 4. 쿠폰 적용
        CouponIssue usedCouponIssue = null;
        Long resolvedCouponIssueId = null;
        int discountAmount = 0;

        if (couponIssueId != null) {
            usedCouponIssue = couponIssueRepository.findByIdWithLock(couponIssueId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

            if (!usedCouponIssue.getMemberId().equals(memberId)) {
                throw new CoreException(ErrorType.FORBIDDEN, "본인의 쿠폰만 사용할 수 있습니다.");
            }

            Coupon coupon = couponRepository.findById(usedCouponIssue.getCouponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));

            int originalTotalPrice = snapshots.stream()
                .mapToInt(s -> s.productPrice() * s.quantity())
                .sum();

            coupon.validateUsable(originalTotalPrice);
            discountAmount = coupon.calculateDiscount(originalTotalPrice);

            usedCouponIssue.use(null);
            resolvedCouponIssueId = couponIssueId;
        }

        // 5. 주문 저장
        Order order = orderRepository.save(
            Order.create(memberId, snapshots, resolvedCouponIssueId, discountAmount));

        // 6. 쿠폰에 주문 ID 연결
        if (usedCouponIssue != null) {
            usedCouponIssue.linkOrder(order.getId());
        }

        return order;
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Order getOrder(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.");
        }
        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getMemberId().equals(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문만 취소할 수 있습니다.");
        }
        order.cancel();
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
            product.increaseStock(item.getQuantity());
        }

        // 쿠폰 복원
        if (order.getCouponIssueId() != null) {
            CouponIssue couponIssue = couponIssueRepository.findById(order.getCouponIssueId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
            couponIssue.cancelUse();
        }
    }

    public List<Order> getOrdersByMemberId(Long memberId, ZonedDateTime startAt, ZonedDateTime endAt) {
        if (startAt != null && endAt != null) {
            return orderRepository.findAllByMemberIdAndCreatedAtBetween(memberId, startAt, endAt);
        }
        return orderRepository.findAllByMemberId(memberId);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public record OrderItemRequest(Long productId, int quantity) {}
}
