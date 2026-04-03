package com.loopers.interfaces.scheduler;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.stock.StockService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.stock.Stock;
import com.loopers.domain.stock.StockRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderExpirationSchedulerTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 주문_만료 {

        @Test
        void CREATED_주문이_만료되면_재고_점유가_해제된다() {
            stockRepository.save(Stock.create(1L, 100));
            stockRepository.save(Stock.create(2L, 50));
            stockService.reserve(Map.of(1L, 10, 2L, 5));

            Order order = createOrderWithItems(1L, Map.of(1L, 10, 2L, 5));

            orderFacade.expireOrder(order.getId());

            Order expired = orderRepository.findByIdWithItems(order.getId()).orElseThrow();
            Stock stock1 = stockRepository.findByProductId(1L).orElseThrow();
            Stock stock2 = stockRepository.findByProductId(2L).orElseThrow();
            assertAll(
                    () -> assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELED),
                    () -> assertThat(stock1.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock1.getAvailableQuantity()).isEqualTo(100),
                    () -> assertThat(stock2.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock2.getAvailableQuantity()).isEqualTo(50)
            );
        }

        @Test
        void CREATED_주문이_만료되면_쿠폰이_복원된다() {
            stockRepository.save(Stock.create(1L, 100));
            stockService.reserve(Map.of(1L, 5));

            IssuedCoupon coupon = issuedCouponRepository.save(
                    IssuedCoupon.create(1L, 1L, "할인쿠폰", CouponType.FIXED, 5000,
                            BigDecimal.valueOf(10000), LocalDateTime.now().plusDays(7)));
            coupon.use();
            issuedCouponRepository.save(coupon);

            Order order = createOrderWithItemsAndCoupon(1L, Map.of(1L, 5), coupon.getId());

            orderFacade.expireOrder(order.getId());

            IssuedCoupon restored = issuedCouponRepository.findById(coupon.getId()).orElseThrow();
            assertThat(restored.isUsed()).isFalse();
        }

        @Test
        void PAID_주문은_만료되지_않고_재고가_유지된다() {
            stockRepository.save(Stock.create(1L, 100));
            stockService.reserve(Map.of(1L, 10));

            Order order = createOrderWithItems(1L, Map.of(1L, 10));
            order.pay();
            orderRepository.save(order);

            orderFacade.expireOrder(order.getId());

            Stock stock = stockRepository.findByProductId(1L).orElseThrow();
            assertAll(
                    () -> assertThat(stock.getReservedQuantity()).isEqualTo(10),
                    () -> assertThat(stock.getAvailableQuantity()).isEqualTo(90)
            );
        }
    }

    @Nested
    class 만료_중_오류_발생 {

        @Test
        void 재고_해제_실패해도_예외가_전파되어_주문_상태가_롤백된다() {
            // 상품 999L에 대한 재고가 없어서 releaseReserved 시 실패
            Order order = createOrderWithItems(1L, Map.of(999L, 3));

            try {
                orderFacade.expireOrder(order.getId());
            } catch (Exception ignored) {
            }

            // TX 롤백으로 주문 상태도 원복
            Order found = orderRepository.findByIdWithItems(order.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(OrderStatus.CREATED);
        }
    }

    private Order createOrderWithItems(Long userId, Map<Long, Integer> productQuantities) {
        Order order = Order.create(userId);
        productQuantities.forEach((productId, quantity) ->
                order.addItem(productId, "상품" + productId, BigDecimal.valueOf(1000), quantity)
        );
        return orderRepository.save(order);
    }

    private Order createOrderWithItemsAndCoupon(Long userId, Map<Long, Integer> productQuantities, Long couponId) {
        Order order = Order.create(userId);
        productQuantities.forEach((productId, quantity) ->
                order.addItem(productId, "상품" + productId, BigDecimal.valueOf(1000), quantity)
        );
        order.applyCoupon(couponId, BigDecimal.valueOf(5000));
        return orderRepository.save(order);
    }
}
