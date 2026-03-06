package com.loopers.domain.order;

import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.RestoreStockItem;
import com.loopers.domain.coupon.CouponDiscount;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository, ProductService productService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
    }

    /**
     * 주문 항목을 검증·스냅샷 후 주문을 생성해 저장한다.
     * 재고 차감은 결제 완료 시점에 수행한다.
     * 쿠폰 미적용 시 couponDiscount는 empty, issuedCouponId는 null.
     */
    @Transactional
    public OrderModel create(Long userId, List<ProductValidationRequest> requests,
                             List<ProductSnapshot> snapshots,
                             Optional<CouponDiscount> couponDiscount,
                             Long issuedCouponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 null일 수 없습니다.");
        }
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 없습니다.");
        }
        if (snapshots == null || snapshots.size() != requests.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목과 스냅샷이 일치하지 않습니다.");
        }
        BigDecimal amountBeforeDiscount;
        BigDecimal discountAmount;
        BigDecimal finalAmount;
        Long couponId = null;
        if (couponDiscount.isPresent()) {
            CouponDiscount d = couponDiscount.get();
            amountBeforeDiscount = d.beforeAmount();
            discountAmount = d.discountAmount();
            finalAmount = d.afterAmount();
            couponId = issuedCouponId;
        } else {
            amountBeforeDiscount = computeAmountBeforeDiscount(snapshots, requests);
            discountAmount = BigDecimal.ZERO;
            finalAmount = amountBeforeDiscount;
        }
        OrderModel order = OrderModel.create(userId, amountBeforeDiscount, discountAmount, finalAmount, couponId);
        for (int i = 0; i < requests.size(); i++) {
            ProductValidationRequest req = requests.get(i);
            order.addItem(OrderItemModel.of(snapshots.get(i), req.quantity(), req.optionId()));
        }
        order.validateHasItems();
        return orderRepository.save(order);
    }

    private static BigDecimal computeAmountBeforeDiscount(List<ProductSnapshot> snapshots,
                                                          List<ProductValidationRequest> requests) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < snapshots.size(); i++) {
            BigDecimal lineTotal = snapshots.get(i).price().value()
                    .multiply(BigDecimal.valueOf(requests.get(i).quantity().value()));
            sum = sum.add(lineTotal);
        }
        return sum;
    }

    /**
     * 쿠폰 미적용 주문. (기존 호환·내부용)
     */
    @Transactional
    public OrderModel create(Long userId, List<ProductValidationRequest> requests) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 null일 수 없습니다.");
        }
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 없습니다.");
        }
        List<ProductSnapshot> snapshots = productService.validateAndGetSnapshots(requests);
        return create(userId, requests, snapshots, Optional.empty(), null);
    }

    /**
     * 주문을 조회한다. 없거나 타인 주문이면 empty.
     * (존재 여부 비노출을 위해 동일하게 처리)
     */
    @Transactional(readOnly = true)
    public Optional<OrderModel> findById(Long userId, Long orderId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getUserId().equals(userId));
    }

    /**
     * 사용자별 주문 목록을 기간·페이징으로 조회한다.
     */
    @Transactional(readOnly = true)
    public List<OrderModel> findOrders(Long userId, ZonedDateTime start, ZonedDateTime end, int page, int size) {
        return orderRepository.findByUserIdAndOrderedAtBetween(userId, start, end, page, size);
    }

    /**
     * 주문을 취소한다. 없거나 타인 주문이면 NOT_FOUND, 취소 불가 상태면 BAD_REQUEST.
     * 결제 완료(PAID) 주문은 재고 복구 후 취소한다. 정합성 우선으로 단일 트랜잭션에서 처리한다.
     * 비관적 락(재고 복구) 점유 시간 최소화를 위해 상태 변경 후 재고 복구를 트랜잭션 말단에서 수행한다.
     */
    @Transactional
    public OrderModel cancel(Long userId, Long orderId) {
        OrderModel order = orderRepository.findById(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        if (!order.canCancel()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 상태입니다: " + order.getStatus());
        }
        List<RestoreStockItem> itemsToRestore = order.getStatus() == OrderStatus.PAID
                ? order.getOrderItems().stream()
                        .map(item -> new RestoreStockItem(item.getProductId(), Quantity.of(item.getQuantity())))
                        .toList()
                : List.of();
        order.cancel();
        if (!itemsToRestore.isEmpty()) {
            productService.restoreStock(itemsToRestore);
        }
        return orderRepository.save(order);
    }
}
