package com.loopers.application.stock;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.gateway.PgType;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StockReconciliationSchedulerTest {

    @Autowired
    private StockReconciliationScheduler stockReconciliationScheduler;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 점유_누수_보정 {

        @Test
        void CANCELED_주문의_점유가_남아있으면_보정_후_점유가_해제된다() {
            stockRepository.save(Stock.create(1L, 100));
            stockRepository.save(Stock.create(2L, 50));
            stockService.reserve(Map.of(1L, 10, 2L, 5));

            Order order = createOrderWithItems(1L, Map.of(1L, 10, 2L, 5));
            order.cancel();
            orderRepository.save(order);

            stockReconciliationScheduler.reconcile();

            Stock stock1 = stockRepository.findByProductId(1L).orElseThrow();
            Stock stock2 = stockRepository.findByProductId(2L).orElseThrow();
            assertAll(
                    () -> assertThat(stock1.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock1.getAvailableQuantity()).isEqualTo(100),
                    () -> assertThat(stock2.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock2.getAvailableQuantity()).isEqualTo(50)
            );
        }
    }

    @Nested
    class 확정_누락_보정 {

        @Test
        void SUCCEEDED_결제와_PAID_주문의_점유가_남아있으면_보정_후_확정된다() {
            stockRepository.save(Stock.create(1L, 100));
            stockService.reserve(Map.of(1L, 10));

            Order order = createOrderWithItems(1L, Map.of(1L, 10));
            order.pay();
            orderRepository.save(order);

            Payment payment = Payment.create(order.getId(), 1L, PgType.TOSS, CardType.SAMSUNG, "1234-5678-9012-3456", BigDecimal.valueOf(10000));
            payment.markSucceeded();
            paymentRepository.save(payment);

            stockReconciliationScheduler.reconcile();

            Stock stock = stockRepository.findByProductId(1L).orElseThrow();
            assertAll(
                    () -> assertThat(stock.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock.getConfirmedQuantity()).isEqualTo(10),
                    () -> assertThat(stock.getAvailableQuantity()).isEqualTo(90)
            );
        }
    }

    @Nested
    class 보정_대상_없음 {

        @Test
        void 보정_대상이_없으면_아무_작업_없이_종료한다() {
            stockRepository.save(Stock.create(1L, 100));

            stockReconciliationScheduler.reconcile();

            Stock stock = stockRepository.findByProductId(1L).orElseThrow();
            assertAll(
                    () -> assertThat(stock.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock.getConfirmedQuantity()).isEqualTo(0),
                    () -> assertThat(stock.getAvailableQuantity()).isEqualTo(100)
            );
        }
    }

    @Nested
    class 보정_중_오류_발생 {

        @Test
        void 한_건_실패해도_다른_건은_계속_진행된다() {
            stockRepository.save(Stock.create(1L, 100));
            stockRepository.save(Stock.create(2L, 50));
            stockService.reserve(Map.of(1L, 10));
            stockService.reserve(Map.of(2L, 5));

            Order order1 = createOrderWithItems(1L, Map.of(1L, 10));
            order1.cancel();
            orderRepository.save(order1);

            // 상품 999L에 대한 재고가 없어서 releaseReserved 시 실패할 주문
            Order order2 = createOrderWithItems(2L, Map.of(999L, 3));
            order2.cancel();
            orderRepository.save(order2);

            stockReconciliationScheduler.reconcile();

            // order1은 정상 보정
            Stock stock1 = stockRepository.findByProductId(1L).orElseThrow();
            assertAll(
                    () -> assertThat(stock1.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(stock1.getAvailableQuantity()).isEqualTo(100)
            );
        }
    }

    private Order createOrderWithItems(Long userId, Map<Long, Integer> productQuantities) {
        Order order = Order.create(userId);
        productQuantities.forEach((productId, quantity) ->
                order.addItem(productId, "상품" + productId, BigDecimal.valueOf(1000), quantity)
        );
        return orderRepository.save(order);
    }
}
