package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private List<OrderItem> createOrderItems() {
        return List.of(
                OrderItem.snapshot(1L, "에어맥스", "나이키", 150000, 2),
                OrderItem.snapshot(2L, "슈퍼스타", "아디다스", 120000, 1)
        );
    }

    private Order createAndSaveOrder(Long userId, String orderNumber) {
        Order order = Order.place(userId, orderNumber, createOrderItems(),
                "홍길동", "010-1234-5678",
                "김철수", "010-9876-5432",
                "06234", "서울시 강남구 테헤란로 123", "4층 401호");
        return orderRepository.save(order);
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        void 새로운_주문을_저장하면_ID가_생성된다() {
            // act
            Order saved = createAndSaveOrder(1L, "ORD-TEST-001");

            // assert
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        void 저장된_주문의_필드가_올바르게_저장된다() {
            // act
            Order saved = createAndSaveOrder(1L, "ORD-TEST-001");

            // assert
            assertThat(saved.getOrderNumber()).isEqualTo("ORD-TEST-001");
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(saved.getOrdererName()).isEqualTo("홍길동");
            assertThat(saved.getItems()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("findById 메서드는")
    class FindById {

        @Test
        void 존재하는_주문을_반환한다() {
            // arrange
            Order saved = createAndSaveOrder(1L, "ORD-TEST-001");

            // act
            Optional<Order> result = orderRepository.findById(saved.getId());

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getOrderNumber()).isEqualTo("ORD-TEST-001");
        }

        @Test
        void 존재하지_않는_주문이면_empty를_반환한다() {
            // act
            Optional<Order> result = orderRepository.findById(999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByUserId 메서드는")
    class FindAllByUserId {

        @Test
        void 날짜_범위_내_주문_목록을_최신순으로_반환한다() {
            // arrange
            createAndSaveOrder(1L, "ORD-TEST-001");
            createAndSaveOrder(1L, "ORD-TEST-002");

            // act
            List<Order> result = orderRepository.findAllByUserId(
                    1L, ZonedDateTime.now().minusMonths(3), ZonedDateTime.now().plusDays(1));

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getOrderNumber()).isEqualTo("ORD-TEST-002");
        }

        @Test
        void 다른_사용자의_주문은_포함되지_않는다() {
            // arrange
            createAndSaveOrder(1L, "ORD-TEST-001");
            createAndSaveOrder(2L, "ORD-TEST-002");

            // act
            List<Order> result = orderRepository.findAllByUserId(
                    1L, ZonedDateTime.now().minusMonths(3), ZonedDateTime.now().plusDays(1));

            // assert
            assertThat(result).hasSize(1);
        }

        @Test
        void 주문이_없으면_빈_목록을_반환한다() {
            // act
            List<Order> result = orderRepository.findAllByUserId(
                    1L, ZonedDateTime.now().minusMonths(3), ZonedDateTime.now().plusDays(1));

            // assert
            assertThat(result).isEmpty();
        }
    }
}
