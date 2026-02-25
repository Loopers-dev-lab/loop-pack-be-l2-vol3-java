package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class OrderRepositoryIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final ProductSnapshot SNAPSHOT = new ProductSnapshot(100L, "상품", new BigDecimal("5000"));

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel createOrderWithOneItem(Long userId) {
        OrderModel order = OrderModel.create(userId);
        order.addItem(OrderItemModel.of(SNAPSHOT, 2, null));
        order.validateHasItems();
        return order;
    }

    @DisplayName("save 시")
    @Nested
    class Save {

        @DisplayName("주문과 항목을 저장한 뒤 findById로 조회할 수 있다.")
        @Test
        void save_shouldPersistOrderAndItemsAndFindById() {
            // given
            OrderModel order = createOrderWithOneItem(USER_ID);

            // when
            OrderModel saved = orderRepository.save(order);
            Optional<OrderModel> found = orderRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
            assertThat(found.get().getUserId()).isEqualTo(USER_ID);
            assertThat(found.get().getStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(found.get().getOrderedAt()).isNotNull();
            assertThat(found.get().getOrderItems()).hasSize(1);
            assertThat(found.get().getOrderItems().get(0).getProductId()).isEqualTo(SNAPSHOT.productId());
            assertThat(found.get().getOrderItems().get(0).getQuantity()).isEqualTo(2);
        }

        @DisplayName("취소 후 저장하면 상태가 반영된다.")
        @Test
        void save_afterCancel_shouldPersistCancelledStatus() {
            // given
            OrderModel order = createOrderWithOneItem(USER_ID);
            OrderModel saved = orderRepository.save(order);
            saved.cancel();

            // when
            orderRepository.save(saved);
            Optional<OrderModel> found = orderRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @DisplayName("존재하지 않는 ID면 empty를 반환한다.")
        @Test
        void findById_withNonExistentId_shouldReturnEmpty() {
            // given
            Long nonExistentId = 999_999L;

            // when
            Optional<OrderModel> found = orderRepository.findById(nonExistentId);

            // then
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("findByUserIdAndOrderedAtBetween 시")
    @Nested
    class FindByUserIdAndOrderedAtBetween {

        @DisplayName("조건에 맞는 주문을 생성일 역순으로 페이징 조회할 수 있다.")
        @Test
        void findByUserIdAndOrderedAtBetween_shouldReturnOrdersInDescOrder() {
            // given
            OrderModel order1 = createOrderWithOneItem(USER_ID);
            OrderModel order2 = createOrderWithOneItem(USER_ID);
            orderRepository.save(order1);
            orderRepository.save(order2);
            ZonedDateTime start = ZonedDateTime.now().minusMinutes(1);
            ZonedDateTime end = ZonedDateTime.now().plusMinutes(1);

            // when
            List<OrderModel> page0 = orderRepository.findByUserIdAndOrderedAtBetween(USER_ID, start, end, 0, 10);

            // then
            assertThat(page0).hasSize(2);
            assertThat(page0.get(0).getOrderedAt()).isAfterOrEqualTo(page0.get(1).getOrderedAt());
        }

        @DisplayName("기간 밖 주문은 조회되지 않는다.")
        @Test
        void findByUserIdAndOrderedAtBetween_whenOutsideRange_shouldReturnEmpty() {
            // given
            OrderModel order = createOrderWithOneItem(USER_ID);
            orderRepository.save(order);
            ZonedDateTime start = ZonedDateTime.now().plusDays(1);
            ZonedDateTime end = ZonedDateTime.now().plusDays(2);

            // when
            List<OrderModel> result = orderRepository.findByUserIdAndOrderedAtBetween(USER_ID, start, end, 0, 10);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("다른 사용자 주문은 조회되지 않는다.")
        @Test
        void findByUserIdAndOrderedAtBetween_whenDifferentUser_shouldReturnEmpty() {
            // given
            OrderModel order = createOrderWithOneItem(USER_ID);
            orderRepository.save(order);
            Long otherUserId = 999L;
            ZonedDateTime start = ZonedDateTime.now().minusMinutes(1);
            ZonedDateTime end = ZonedDateTime.now().plusMinutes(1);

            // when
            List<OrderModel> result = orderRepository.findByUserIdAndOrderedAtBetween(otherUserId, start, end, 0, 10);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("page·size에 따라 페이징된다.")
        @Test
        void findByUserIdAndOrderedAtBetween_shouldRespectPageAndSize() {
            // given
            OrderModel order1 = createOrderWithOneItem(USER_ID);
            OrderModel order2 = createOrderWithOneItem(USER_ID);
            orderRepository.save(order1);
            orderRepository.save(order2);
            ZonedDateTime start = ZonedDateTime.now().minusMinutes(1);
            ZonedDateTime end = ZonedDateTime.now().plusMinutes(1);

            // when
            List<OrderModel> page0Size1 = orderRepository.findByUserIdAndOrderedAtBetween(USER_ID, start, end, 0, 1);
            List<OrderModel> page1Size1 = orderRepository.findByUserIdAndOrderedAtBetween(USER_ID, start, end, 1, 1);

            // then
            assertThat(page0Size1).hasSize(1);
            assertThat(page1Size1).hasSize(1);
            assertThat(page0Size1.get(0).getId()).isNotEqualTo(page1Size1.get(0).getId());
        }
    }
}
