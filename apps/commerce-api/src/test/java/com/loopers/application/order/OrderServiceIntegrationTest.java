package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
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

            product.update("런닝화", new BigDecimal("70000"), null, null);
            productRepository.save(product);

            Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getName()).isEqualTo("런닝화");
            assertThat(updatedProduct.getPrice()).isEqualByComparingTo(new BigDecimal("70000"));

            assertThat(order.getOrderItems().get(0).getProductName()).isEqualTo("운동화");
            assertThat(order.getOrderItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        }
    }
}
