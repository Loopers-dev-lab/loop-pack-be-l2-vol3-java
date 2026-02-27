package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartService;
import com.loopers.domain.order.OrderCartRestoreModel;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemSnapshot;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.enums.OrderType;
import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 주문 Facade (퍼사드)
 *
 * <p>UserService, OrderService, ProductService, BrandService, StockService, CartService를
 * 조합(orchestration)하여 주문 비즈니스 플로우를 완성한다.</p>
 *
 * <ul>
 *   <li>사용자 인증</li>
 *   <li>트랜잭션 경계 설정</li>
 *   <li>직접 주문(DIRECT) 및 장바구니 주문(CART) 생성 — 상품 검증, 재고 hold, 스냅샷 생성</li>
 *   <li>주문 취소/만료 — CAS 상태 전이 후 재고 release, 장바구니 복원</li>
 *   <li>주문 목록 조회, 상세 조회</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderFacade {

    private final OrderService orderService;
    private final UserService userService;
    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;
    private final CartService cartService;

    /**
     * 직접 주문을 생성한다.
     *
     * <p>상품 상세 페이지에서 바로 주문하는 DIRECT 주문 방식이다.
     * 상품 검증 → 브랜드 조회 → 재고 hold → 주문 저장 순으로 진행하며,
     * 재고 hold 실패 시 이미 hold된 재고를 역순으로 release한다.</p>
     *
     * @param loginId 로그인 ID
     * @param loginPw 로그인 비밀번호
     * @param items   주문할 상품 항목 목록
     * @return 생성된 주문 정보
     */
    @Transactional
    public OrderInfo createDirectOrder(String loginId, String loginPw, List<OrderItemCommand> items) {
        UserModel user = userService.authenticate(loginId, loginPw);
        return processOrder(user.getUserId(), OrderType.DIRECT, items);
    }

    /**
     * 장바구니 주문을 생성한다.
     *
     * <p>장바구니에서 선택한 상품들을 주문하는 CART 주문 방식이다.
     * 상품 검증 → 브랜드 조회 → 재고 hold → 주문 저장 순으로 진행하며,
     * 재고 hold 실패 시 이미 hold된 재고를 역순으로 release한다.</p>
     *
     * @param loginId 로그인 ID
     * @param loginPw 로그인 비밀번호
     * @param items   주문할 상품 항목 목록
     * @return 생성된 주문 정보
     */
    @Transactional
    public OrderInfo createCartOrder(String loginId, String loginPw, List<OrderItemCommand> items) {
        UserModel user = userService.authenticate(loginId, loginPw);
        return processOrder(user.getUserId(), OrderType.CART, items);
    }

    /**
     * 주문 생성 공통 로직.
     * <p>
     * 주문 항목 검증/병합 → 상품 조회 → 브랜드 조회 → 재고 hold → 스냅샷 생성 → 주문 저장.
     * 데드락 방지를 위해 productId 오름차순으로 재고 예약을 수행한다.
     * 재고 hold 실패 시 이미 hold된 재고를 역순으로 release한다.
     * </p>
     */
    private OrderInfo processOrder(String userId, OrderType orderType, List<OrderItemCommand> items) {
        List<OrderItemCommand> merged = orderService.validateAndPrepare(userId, items);

        List<OrderItemSnapshot> snapshots = new ArrayList<>();
        List<String> heldProductIds = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        try {
            for (OrderItemCommand item : merged) {
                ProductModel product = productService.findOrderableById(item.productId());
                BrandModel brand = brandService.findById(product.getBrandId());
                stockService.hold(item.productId(), item.quantity());
                heldProductIds.add(item.productId());

                OrderItemSnapshot snapshot = OrderItemSnapshot.from(product, brand, item.quantity());
                totalAmount = totalAmount.add(snapshot.lineTotal());
                snapshots.add(snapshot);
            }
        } catch (CoreException e) {
            compensateHeldStocks(heldProductIds, merged);
            throw e;
        }

        OrderModel order = orderService.createOrder(userId, orderType, totalAmount, snapshots);
        List<OrderItemModel> orderItems = orderService.findOrderItems(order.getOrderId());
        return OrderInfo.from(order, orderItems);
    }



    /**
     * 주문을 취소한다.
     *
     * <p>CAS 상태 전이 후 재고를 해제하고, DIRECT 주문인 경우 장바구니를 복원한다.</p>
     *
     * @param loginId 로그인 ID
     * @param loginPw 로그인 비밀번호
     * @param orderId 취소할 주문 ID
     */
    @Transactional
    public void cancelOrder(String loginId, String loginPw, String orderId) {
        UserModel user = userService.authenticate(loginId, loginPw);
        Optional<OrderModel> order = orderService.cancelOrder(user.getUserId(), orderId);
        order.ifPresent(o -> releaseStocksAndRestore(o, RestoreReason.USER_CANCELLED, RestoreTriggerSource.CANCEL_API));
    }

    /**
     * 배치 스케줄러가 주문을 만료 처리한다.
     *
     * <p>CAS 상태 전이 후 재고를 해제하고, DIRECT 주문인 경우 장바구니를 복원한다.
     * 인증이 필요하지 않은 시스템 내부 호출용 메서드이다.</p>
     *
     * @param orderId 주문 ID
     */
    @Transactional
    public void expireOrder(String orderId) {
        Optional<OrderModel> order = orderService.expireOrder(orderId);
        order.ifPresent(o -> releaseStocksAndRestore(o, RestoreReason.EXPIRED, RestoreTriggerSource.EXPIRE_JOB));
    }

    /**
     * 재고 hold 실패 시 이미 hold된 재고를 역순으로 release한다.
     */
    private void compensateHeldStocks(List<String> heldProductIds, List<OrderItemCommand> merged) {
        for (int i = heldProductIds.size() - 1; i >= 0; i--) {
            String heldProductId = heldProductIds.get(i);
            int qty = merged.stream()
                    .filter(m -> m.productId().equals(heldProductId))
                    .findFirst().map(OrderItemCommand::quantity).orElse(0);
            stockService.release(heldProductId, qty);
        }
    }

    /**
     * 재고 해제 및 장바구니 복원을 수행한다.
     * <p>
     * 주문 항목의 재고를 productId 오름차순으로 해제하고,
     * DIRECT 주문인 경우 장바구니 복원을 수행한다.
     * PK 충돌 시 이미 복원된 것으로 간주하여 skip한다 (멱등 보장).
     * </p>
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
