package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderCartRestoreModel;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.OrderType;
import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 주문 Facade (퍼사드)
 *
 * <p>OrderService, ProductService, BrandService, StockService, CartService, CouponService를
 * 조합(orchestration)하여 주문 비즈니스 플로우를 완성한다.</p>
 *
 * <ul>
 *   <li>트랜잭션 경계 설정</li>
 *   <li>직접 주문(DIRECT) 및 장바구니 주문(CART) 생성 — 쿠폰 검증, 상품 검증, 재고 hold, 스냅샷 생성</li>
 *   <li>주문 취소/만료 — CAS 상태 전이 후 재고 release, 장바구니 복원</li>
 *   <li>주문 목록 조회, 상세 조회</li>
 * </ul>
 *
 * <p>인증은 {@link com.loopers.interfaces.api.CustomerAuthInterceptor}에서 처리된다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;
    private final CartService cartService;
    private final CouponService couponService;

    /**
     * 직접 주문을 생성한다.
     *
     * @param userId       사용자 ID
     * @param items        주문할 상품 항목 목록
     * @param userCouponId 적용할 발급 쿠폰 ID (없으면 null)
     * @return 생성된 주문 정보
     */
    @Transactional
    public OrderInfo createDirectOrder(Long userId, List<OrderItemCommand> items, Long userCouponId) {
        return processOrder(userId, OrderType.DIRECT, items, userCouponId);
    }

    /**
     * 장바구니 주문을 생성한다.
     *
     * @param userId       사용자 ID
     * @param items        주문할 상품 항목 목록
     * @param userCouponId 적용할 발급 쿠폰 ID (없으면 null)
     * @return 생성된 주문 정보
     */
    @Transactional
    public OrderInfo createCartOrder(Long userId, List<OrderItemCommand> items, Long userCouponId) {
        return processOrder(userId, OrderType.CART, items, userCouponId);
    }

    /**
     * 주문 목록을 조회한다 (기간 필터).
     */
    public List<OrderInfo> getOrders(Long userId, LocalDateTime start, LocalDateTime end) {
        List<OrderModel> orders = orderService.findAllByUserId(userId, start, end);
        Map<Long, List<OrderItemModel>> itemMap = orderService.batchLoadOrderItems(orders);
        return orders.stream()
                .map(order -> OrderInfo.from(order, itemMap.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }

    /**
     * 주문 상세 정보를 조회한다.
     */
    public OrderInfo getOrderDetail(Long userId, Long orderId) {
        OrderModel order = orderService.findByIdAndUserId(orderId, userId);
        List<OrderItemModel> items = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, items);
    }

    /**
     * 관리자용 전체 주문 목록을 조회한다 (기간 필터).
     */
    public List<OrderInfo> getOrdersForAdmin(LocalDateTime start, LocalDateTime end) {
        List<OrderModel> orders = orderService.findAllOrders(start, end);
        Map<Long, List<OrderItemModel>> itemMap = orderService.batchLoadOrderItems(orders);
        return orders.stream()
                .map(order -> OrderInfo.from(order, itemMap.getOrDefault(order.getOrderId(), List.of())))
                .toList();
    }

    /**
     * 관리자용 주문 상세 정보를 조회한다.
     */
    public OrderInfo getOrderDetailForAdmin(Long orderId) {
        OrderModel order = orderService.findOrderById(orderId);
        List<OrderItemModel> items = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, items);
    }

    /**
     * 주문 생성 공통 로직.
     * <p>
     * 쿠폰 검증 → 재고 hold → 할인 배분 → 주문 저장 → 쿠폰 사용 처리.
     * 데드락 방지를 위해 productId 오름차순으로 재고 예약을 수행한다.
     * 재고 hold 실패 시 이미 hold된 재고를 역순으로 release한다.
     * </p>
     */
    private OrderInfo processOrder(Long userId, OrderType orderType, List<OrderItemCommand> items,
                                    Long userCouponId) {
        List<OrderItemCommand> merged = orderService.validateAndPrepare(userId, items);

        // Step 1: 쿠폰 검증 (재고 hold 전에 수행 → 보상 복잡도 최소화)
        UserCouponModel userCoupon = null;
        CouponModel coupon = null;
        if (userCouponId != null) {
            userCoupon = couponService.validateAndGetUserCoupon(userId, userCouponId);
            coupon = couponService.findByIdForAdmin(userCoupon.getCouponId());
        }

        // Step 2: 재고 hold + 스냅샷 생성 + 쿠폰 할인 계산
        List<OrderItemSnapshot> rawSnapshots = new ArrayList<>();
        List<Long> heldProductIds = new ArrayList<>();
        BigDecimal totalOriginalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        try {
            for (OrderItemCommand item : merged) {
                ProductModel product = productService.findOrderableById(item.productId());
                BrandModel brand = brandService.findById(product.getBrandId());
                stockService.hold(item.productId(), item.quantity());
                heldProductIds.add(item.productId());

                OrderItemSnapshot snapshot = OrderItemSnapshot.from(product, brand, item.quantity());
                totalOriginalAmount = totalOriginalAmount.add(snapshot.originalAmount());
                rawSnapshots.add(snapshot);
            }

            // Step 3: 쿠폰 할인 금액 계산 (try 안에서 수행 → 실패 시 보상)
            if (coupon != null) {
                coupon.validateApplicable(totalOriginalAmount);
                totalDiscount = coupon.calculateDiscount(totalOriginalAmount);
            }
        } catch (CoreException e) {
            compensateHeldStocks(heldProductIds, merged);
            throw e;
        }

        List<OrderItemSnapshot> finalSnapshots = applyDiscountProportionally(rawSnapshots, totalOriginalAmount,
                totalDiscount);
        BigDecimal totalFinalAmount = totalOriginalAmount.subtract(totalDiscount);

        // Step 4: 주문 생성
        OrderModel order = orderService.createOrder(userId, orderType, totalFinalAmount, finalSnapshots);

        // Step 5: 쿠폰 사용 처리 (동일 트랜잭션)
        if (userCoupon != null) {
            couponService.markCouponAsUsed(userCoupon.getUserCouponId(), order.getOrderId());
        }

        List<OrderItemModel> orderItems = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, orderItems);
    }

    /**
     * 할인 금액을 주문 항목별 originalAmount 비율로 배분한다.
     * 반올림 오차는 마지막 항목이 흡수한다.
     */
    private List<OrderItemSnapshot> applyDiscountProportionally(List<OrderItemSnapshot> rawSnapshots,
                                                                  BigDecimal totalOriginalAmount,
                                                                  BigDecimal totalDiscount) {
        if (totalDiscount.compareTo(BigDecimal.ZERO) == 0) {
            return rawSnapshots;
        }

        List<OrderItemSnapshot> result = new ArrayList<>();
        BigDecimal allocatedDiscount = BigDecimal.ZERO;

        for (int i = 0; i < rawSnapshots.size(); i++) {
            OrderItemSnapshot raw = rawSnapshots.get(i);
            boolean isLast = (i == rawSnapshots.size() - 1);

            BigDecimal itemDiscount;
            if (isLast) {
                itemDiscount = totalDiscount.subtract(allocatedDiscount);
            } else {
                itemDiscount = totalDiscount
                        .multiply(raw.originalAmount())
                        .divide(totalOriginalAmount, 0, RoundingMode.FLOOR);
                allocatedDiscount = allocatedDiscount.add(itemDiscount);
            }

            OrderItemSnapshot snapshotWithDiscount = new OrderItemSnapshot(
                    raw.productId(), raw.quantity(), raw.productName(),
                    raw.unitPrice(), raw.brandId(), raw.brandName(), raw.imageUrl(),
                    raw.originalAmount(), itemDiscount, raw.originalAmount().subtract(itemDiscount));
            result.add(snapshotWithDiscount);
        }
        return result;
    }

    /**
     * 주문을 취소한다.
     */
    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Optional<OrderModel> order = orderService.cancelOrder(userId, orderId);
        order.ifPresent(o -> releaseStocksAndRestore(o, RestoreReason.USER_CANCELLED, RestoreTriggerSource.CANCEL_API));
    }

    /**
     * 배치 스케줄러가 주문을 만료 처리한다.
     */
    @Transactional
    public void expireOrder(Long orderId) {
        Optional<OrderModel> order = orderService.expireOrder(orderId);
        order.ifPresent(o -> releaseStocksAndRestore(o, RestoreReason.EXPIRED, RestoreTriggerSource.EXPIRE_JOB));
    }

    /**
     * 재고 hold 실패 시 이미 hold된 재고를 역순으로 release한다.
     */
    private void compensateHeldStocks(List<Long> heldProductIds, List<OrderItemCommand> merged) {
        for (int i = heldProductIds.size() - 1; i >= 0; i--) {
            Long heldProductId = heldProductIds.get(i);
            int qty = merged.stream()
                    .filter(m -> m.productId().equals(heldProductId))
                    .findFirst().map(OrderItemCommand::quantity).orElse(0);
            stockService.release(heldProductId, qty);
        }
    }

    /**
     * 재고 해제 및 장바구니 복원을 수행한다.
     */
    private void releaseStocksAndRestore(OrderModel order, RestoreReason reason,
                                          RestoreTriggerSource triggerSource) {
        List<OrderItemModel> orderItems = orderService.findOrderItems(order.getOrderId());

        List<OrderItemModel> sorted = orderItems.stream()
                .sorted(Comparator.comparing(OrderItemModel::getProductId))
                .toList();
        for (OrderItemModel item : sorted) {
            stockService.release(item.getProductId(), item.getQuantity());
        }

        // 쿠폰 복원 (멱등)
        couponService.restoreCoupon(order.getOrderId());

        if (order.getOrderType() == OrderType.DIRECT) {
            if (!orderService.existsCartRestore(order.getOrderId())) {
                orderService.saveCartRestore(
                        OrderCartRestoreModel.create(order.getOrderId(), order.getUserId(),
                                reason, triggerSource));
                List<CartService.RestoreItem> restoreItems = orderItems.stream()
                        .map(item -> new CartService.RestoreItem(item.getProductId(), item.getQuantity()))
                        .toList();
                cartService.restoreFromOrder(order.getUserId(), restoreItems);
            }
        }
    }
}
