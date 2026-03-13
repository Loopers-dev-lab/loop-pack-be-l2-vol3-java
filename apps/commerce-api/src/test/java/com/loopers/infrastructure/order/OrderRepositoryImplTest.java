package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(OrderRepositoryImpl.class)
@ActiveProfiles("test")
@DisplayName("OrderRepository 통합 테스트")
class OrderRepositoryImplTest {

    @Autowired
    OrderRepositoryImpl orderRepository;

    @Autowired
    TestEntityManager entityManager;

    private OrderModel createOrder(Long userId) {
        return OrderModel.create(userId, OrderType.DIRECT, BigDecimal.valueOf(50000));
    }

    @Test
    @DisplayName("저장 시 ID가 자동 생성된다")
    void save_ShouldPersistWithAutoId() {
        OrderModel order = createOrder(1L);

        OrderModel saved = orderRepository.save(order);

        assertThat(saved.getOrderId()).isNotNull();
        assertThat(saved.getOrderId()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("ID로 조회 - 존재하는 주문")
    void findById_Existing_ShouldReturn() {
        OrderModel saved = orderRepository.save(createOrder(1L));

        Optional<OrderModel> found = orderRepository.findById(saved.getOrderId());

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("주문 ID + 사용자 ID로 조회 - 소유자만 조회 가능")
    void findByIdAndUserId_ShouldReturn() {
        OrderModel saved = orderRepository.save(createOrder(1L));

        Optional<OrderModel> found = orderRepository.findByIdAndUserId(saved.getOrderId(), 1L);
        Optional<OrderModel> notFound = orderRepository.findByIdAndUserId(saved.getOrderId(), 2L);

        assertThat(found).isPresent();
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("CAS 상태 전이 - PENDING → CANCELLED 성공 시 affected=1")
    void casUpdateStatus_PendingToCancelled_ShouldReturnAffectedRows1() {
        OrderModel saved = orderRepository.save(createOrder(1L));
        entityManager.flush();
        entityManager.clear();

        int affected = orderRepository.casUpdateStatus(
                saved.getOrderId(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);

        assertThat(affected).isEqualTo(1);
        OrderModel updated = orderRepository.findById(saved.getOrderId()).get();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("CAS 상태 전이 - 이미 CANCELLED인 주문에 PENDING→CANCELLED 시도 시 affected=0")
    void casUpdateStatus_AlreadyCancelled_ShouldReturnAffectedRows0() {
        OrderModel order = createOrder(1L);
        OrderModel saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        orderRepository.casUpdateStatus(saved.getOrderId(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
        entityManager.flush();
        entityManager.clear();

        int affected = orderRepository.casUpdateStatus(
                saved.getOrderId(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("만료 대상 조회 - status=PENDING_PAYMENT AND expiresAt < now() 인 주문만 반환")
    void findExpiredPendingOrders_ShouldReturnOnlyExpired() {
        // 아직 만료되지 않은 주문 (expiresAt은 15분 후)
        orderRepository.save(createOrder(1L));
        entityManager.flush();

        List<OrderModel> result = orderRepository.findExpiredPendingOrders();

        // 방금 생성한 주문은 expiresAt이 미래이므로 포함되지 않아야 함
        assertThat(result).isEmpty();
    }
}
