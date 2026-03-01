package com.loopers.application.order;

import com.loopers.application.user.UserApplicationService;
import com.loopers.application.user.UserDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.Gender;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderApplicationServiceIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private UserApplicationService userApplicationService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        UserDto.UserInfo user = userApplicationService.register(
            "orderuser1", "TestPass1!", "주문테스터",
            LocalDate.of(2000, 1, 1), "order1@loopers.com", Gender.MALE
        );
        userId = user.id();

        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("ORDER_BRAND"), new BrandDescription("주문 브랜드"))
        );

        Product product = productJpaRepository.save(
            Product.create(brand.getId(), "주문 상품", "설명", BigDecimal.valueOf(25000), 5)
        );
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 생성 시 재고가 차감되고 스냅샷 정보가 저장된다")
    @Test
    void placeOrder_success() {
        OrderDto.OrderInfo result = orderApplicationService.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 2))
        );

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().productId()).isEqualTo(productId);
        assertThat(result.items().getFirst().productName()).isEqualTo("주문 상품");
        assertThat(result.totalAmount()).isEqualByComparingTo("50000");

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(3);
    }

    @DisplayName("재고가 부족하면 주문 생성에 실패하고 재고는 유지된다")
    @Test
    void placeOrder_fail_notEnoughStock() {
        assertThatThrownBy(() ->
            orderApplicationService.placeOrder(
                userId,
                List.of(new OrderDto.OrderLineCommand(productId, 6))
            )
        )
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_INSUFFICIENT_STOCK));

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(5);
    }

    @DisplayName("사용자 주문 상세 조회 시 본인 주문을 조회할 수 있다")
    @Test
    void getOrder_success() {
        OrderDto.OrderInfo created = orderApplicationService.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1))
        );

        OrderDto.OrderInfo found = orderApplicationService.getOrder(userId, created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.items()).hasSize(1);
        assertThat(found.totalAmount()).isEqualByComparingTo("25000");
    }

    @DisplayName("존재하지 않는 주문 상세 조회 시 예외가 발생한다")
    @Test
    void getOrder_fail_notFound() {
        assertThatThrownBy(() -> orderApplicationService.getOrder(userId, 9999L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.ORDER_NOT_FOUND));
    }

    @DisplayName("기간 내 주문 목록 조회가 가능하다")
    @Test
    void getOrders_success() {
        orderApplicationService.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1))
        );

        ZonedDateTime startAt = ZonedDateTime.now().minusDays(1);
        ZonedDateTime endAt = ZonedDateTime.now().plusDays(1);
        List<OrderDto.OrderInfo> orders = orderApplicationService.getOrders(userId, startAt, endAt);

        assertThat(orders).hasSize(1);
    }

    @DisplayName("주문 취소 시 상태가 CANCELLED로 변경되고 재고가 복구된다")
    @Test
    void cancelOrder_success() {
        OrderDto.OrderInfo created = orderApplicationService.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 2))
        );

        OrderDto.OrderInfo cancelled = orderApplicationService.cancelOrder(userId, created.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(5);
    }

    @DisplayName("이미 취소된 주문을 다시 취소해도 멱등하게 성공한다")
    @Test
    void cancelOrder_idempotent() {
        OrderDto.OrderInfo created = orderApplicationService.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1))
        );
        orderApplicationService.cancelOrder(userId, created.id());

        OrderDto.OrderInfo cancelledAgain = orderApplicationService.cancelOrder(userId, created.id());

        assertThat(cancelledAgain.status()).isEqualTo(OrderStatus.CANCELLED);
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(5);
    }
}
