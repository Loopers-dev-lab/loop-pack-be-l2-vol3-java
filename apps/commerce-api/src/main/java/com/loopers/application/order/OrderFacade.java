package com.loopers.application.order;

import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.application.coupon.IssuedCouponSnapshot;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.product.ProductService;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.QueueFacade;
import com.loopers.application.queue.SessionService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private static final int FALLBACK_SEMAPHORE_PERMITS = 30;

    private final OrderService orderService;
    private final ProductService productService;
    private final StockService stockService;
    private final IssuedCouponService issuedCouponService;
    private final PaymentFacade paymentFacade;
    private final ModeManager modeManager;
    private final QueueFacade queueFacade;

    private final Semaphore fallbackSemaphore = new Semaphore(FALLBACK_SEMAPHORE_PERMITS);

    // Command

    @Transactional
    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {
        boolean isEventMode = (modeManager.isEvent() || modeManager.isDrain())
                && !modeManager.isInGracePeriod()
                && !modeManager.isFallbackMode();

        boolean isFallbackOrder = modeManager.isFallbackMode()
                && (modeManager.isEvent() || modeManager.isDrain());

        if (isEventMode) {
            long casResult = queueFacade.consumeSession(userId);
            if (casResult == SessionService.CAS_KEY_NOT_FOUND) {
                throw new CoreException(ErrorType.UNAUTHORIZED, "세션이 만료되었습니다. 대기열에 다시 진입해주세요");
            }
            if (casResult == SessionService.CAS_STATUS_MISMATCH) {
                throw new CoreException(ErrorType.CONFLICT, "이미 주문이 진행 중입니다");
            }
        }

        if (isFallbackOrder && !fallbackSemaphore.tryAcquire()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "일시적으로 주문이 제한됩니다. 잠시 후 다시 시도해주세요");
        }

        try {
            OrderInfo result = executeOrder(userId, command);

            if (isEventMode) {
                registerSessionDeletion(userId);
            }

            return result;
        } catch (Exception e) {
            if (isEventMode) {
                queueFacade.restoreSession(userId);
            }
            throw e;
        } finally {
            if (isFallbackOrder) {
                fallbackSemaphore.release();
            }
        }
    }

    @Transactional
    public void expireOrder(Long orderId) {
        boolean expired = orderService.expireIfCreated(orderId);
        if (!expired) return;

        Order order = orderService.getOrder(orderId);
        stockService.releaseReserved(order.getProductQuantities());

        if (order.hasCoupon()) {
            issuedCouponService.restore(order.getIssuedCouponId());
        }
    }

    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        order.validateOwnership(userId);
        if (!order.isPaid()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 주문 상태입니다");
        }

        paymentFacade.cancelPayment(userId, orderId);
    }

    // Query

    @Transactional(readOnly = true)
    public OrderInfo getOrderDetail(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        order.validateOwnership(userId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderSummary> getOrderList(Long userId, OrderStatus status, ZonedDateTime startDateTime, ZonedDateTime endDateTime, Pageable pageable) {
        Page<Order> orders = orderService.findOrdersByUserIdAndStatusAndDateRange(userId, status, startDateTime, endDateTime, pageable);
        return orders.map(OrderInfo.OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getAdminOrderDetail(Long orderId) {
        Order order = orderService.getOrder(orderId);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrderList(OrderStatus status, Pageable pageable) {
        Page<Order> orders = (status != null)
                ? orderService.findOrdersByStatus(status, pageable)
                : orderService.findAllOrders(pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo.OrderAdminSummary> getAdminOrdersByProduct(Long productId, Pageable pageable) {
        Page<Order> orders = orderService.findOrdersByProductId(productId, pageable);
        return orders.map(OrderInfo.OrderAdminSummary::from);
    }

    private OrderInfo executeOrder(Long userId, OrderCommand.Place command) {
        Map<Long, Integer> productQuantities = command.toQuantityMap();
        List<Product> products = productService.getActiveProducts(productQuantities.keySet());

        List<OrderCommand.CreateItem> orderItems = command.toCreateItems(products);
        BigDecimal totalAmount = OrderCommand.CreateItem.calculateTotalAmount(orderItems);

        IssuedCouponSnapshot couponSnapshot = command.issuedCouponId() != null
                ? issuedCouponService.createDiscountSnapshot(command.issuedCouponId(), userId, totalAmount)
                : IssuedCouponSnapshot.none();

        stockService.reserve(productQuantities);

        if (couponSnapshot.isApplied()) {
            issuedCouponService.markUsedIfAvailable(command.issuedCouponId(), userId);
        }

        OrderCommand.CouponSnapshot orderCoupon = OrderCommand.CouponSnapshot.of(
                couponSnapshot.issuedCouponId(), couponSnapshot.discountAmount());

        Order order = orderService.createOrder(OrderCommand.Create.of(userId, orderItems, orderCoupon));
        return OrderInfo.from(order);
    }

    private void registerSessionDeletion(Long userId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                queueFacade.deleteSessionAfterCommit(userId);
            }
        });
    }
}
