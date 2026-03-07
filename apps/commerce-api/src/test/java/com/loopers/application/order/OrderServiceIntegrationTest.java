package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 주문_생성 {

        @Test
        void 주문_시점의_상품_정보를_스냅샷으로_저장한다() {
            OrderCommand.Create command = OrderCommand.Create.of(1L, List.of(
                    OrderCommand.CreateItem.of(1L, "운동화", new BigDecimal("50000"), 2)
            ));

            Order order = orderService.createOrder(command);

            assertThat(order.getId()).isNotNull();
            assertThat(order.getOrderItems()).hasSize(1);
            assertThat(order.getOrderItems().get(0).getProductName()).isEqualTo("운동화");
            assertThat(order.getOrderItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        void 원본_상품이_수정되어도_주문_스냅샷은_영향받지_않는다() {
            Product product = productRepository.save(
                    Product.create(1L, "운동화", new BigDecimal("50000"), 100, "편한 운동화")
            );
            OrderCommand.Create command = OrderCommand.Create.of(1L, List.of(
                    OrderCommand.CreateItem.of(product.getId(), "운동화", new BigDecimal("50000"), 2)
            ));
            Order order = orderService.createOrder(command);

            product.updateInfo("런닝화", new BigDecimal("70000"), null, null);
            productRepository.save(product);

            Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getName()).isEqualTo("런닝화");
            assertThat(updatedProduct.getPrice()).isEqualByComparingTo(new BigDecimal("70000"));

            assertThat(order.getOrderItems().get(0).getProductName()).isEqualTo("운동화");
            assertThat(order.getOrderItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        }
    }

    @Nested
    class 주문_상세_조회 {

        @Test
        void 주문_ID로_조회하면_주문_정보를_반환한다() {
            Order created = orderService.createOrder(OrderCommand.Create.of(1L, List.of(
                    OrderCommand.CreateItem.of(1L, "운동화", new BigDecimal("50000"), 2)
            )));

            Order order = orderService.getOrder(created.getId());

            assertThat(order.getId()).isEqualTo(created.getId());
            assertThat(order.getUserId()).isEqualTo(1L);
            assertThat(order.getOrderItems()).hasSize(1);
            assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        }

        @Test
        void 존재하지_않는_주문이면_예외() {
            assertThatThrownBy(() -> orderService.getOrder(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    class 주문_목록_조회 {

        @Test
        void 사용자_ID로_조회하면_해당_사용자의_주문만_반환한다() {
            orderService.createOrder(OrderCommand.Create.of(1L, List.of(
                    OrderCommand.CreateItem.of(1L, "운동화", new BigDecimal("50000"), 1)
            )));
            orderService.createOrder(OrderCommand.Create.of(1L, List.of(
                    OrderCommand.CreateItem.of(2L, "셔츠", new BigDecimal("30000"), 1)
            )));
            orderService.createOrder(OrderCommand.Create.of(2L, List.of(
                    OrderCommand.CreateItem.of(3L, "바지", new BigDecimal("40000"), 1)
            )));

            Page<Order> result = orderService.findOrdersByUserIdAndDateRange(1L, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).allMatch(order -> order.getUserId().equals(1L));
        }

        @Test
        void 페이징이_올바르게_동작한다() {
            for (int i = 0; i < 5; i++) {
                orderService.createOrder(OrderCommand.Create.of(1L, List.of(
                        OrderCommand.CreateItem.of((long) (i + 1), "상품" + i, new BigDecimal("10000"), 1)
                )));
            }

            Page<Order> result = orderService.findOrdersByUserIdAndDateRange(1L, null, null, PageRequest.of(0, 2));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(5);
            assertThat(result.getTotalPages()).isEqualTo(3);
        }
    }

    @Nested
    class 전체_주문_목록_조회_관리자 {

        @Test
        void 모든_사용자의_주문을_반환한다() {
            orderService.createOrder(OrderCommand.Create.of(1L, List.of(
                    OrderCommand.CreateItem.of(1L, "운동화", new BigDecimal("50000"), 1)
            )));
            orderService.createOrder(OrderCommand.Create.of(2L, List.of(
                    OrderCommand.CreateItem.of(2L, "셔츠", new BigDecimal("30000"), 1)
            )));
            orderService.createOrder(OrderCommand.Create.of(3L, List.of(
                    OrderCommand.CreateItem.of(3L, "바지", new BigDecimal("40000"), 1)
            )));

            Page<Order> result = orderService.findAllOrders(PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(3);
        }

        @Test
        void 페이징이_올바르게_동작한다() {
            for (int i = 0; i < 5; i++) {
                orderService.createOrder(OrderCommand.Create.of((long) (i + 1), List.of(
                        OrderCommand.CreateItem.of((long) (i + 1), "상품" + i, new BigDecimal("10000"), 1)
                )));
            }

            Page<Order> result = orderService.findAllOrders(PageRequest.of(0, 2));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(5);
            assertThat(result.getTotalPages()).isEqualTo(3);
        }
    }
}
