package com.loopers.domain.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class OrderServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long NOT_EXISTED_ORDER_ID = 999L;
    private static final Money VALID_PRICE = new Money(10000);
    private static final Money ZERO_DISCOUNT = new Money(0);
    private static final Stock VALID_STOCK = new Stock(100);
    private static final Quantity ORDER_QUANTITY = new Quantity(2);

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderItem buildOrderItem(Product product, String brandName) {
        return new OrderItem(product.getId(), ORDER_QUANTITY, product.getName(), brandName, product.getPrice());
    }

    private Order saveOrder(Long userId, Product product, String brandName) {
        Money originalAmount = new Money(product.getPrice().getAmount() * ORDER_QUANTITY.getValue());
        return orderJpaRepository.save(new Order(userId,
                List.of(buildOrderItem(product, brandName)), null, originalAmount, ZERO_DISCOUNT));
    }

    @DisplayName("주문 생성 시")
    @Nested
    class Create {

        @DisplayName("정상적인 주문 항목으로 주문이 저장된다.")
        @Test
        void createsOrder_whenValidOrderItems() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, VALID_STOCK));
            List<OrderItem> items = List.of(buildOrderItem(product, brand.getName()));
            Money originalAmount = new Money(VALID_PRICE.getAmount() * ORDER_QUANTITY.getValue());

            // act
            Order result = orderService.create(USER_ID, items, null, originalAmount, ZERO_DISCOUNT);

            // assert
            assertThat(result.getId()).isPositive();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getOrderItems()).hasSize(1);
            assertThat(result.getOriginalAmount().getAmount()).isEqualTo(20000);
            assertThat(result.getDiscountAmount().getAmount()).isEqualTo(0);
            assertThat(result.getFinalAmount().getAmount()).isEqualTo(20000);

            // DB 저장 확인
            Order saved = orderJpaRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.getOrderItems()).hasSize(1);
            assertThat(saved.getOrderItems().get(0).getProductName()).isEqualTo("나이키 에어맥스");
        }
    }

    @DisplayName("주문 단건 조회 시")
    @Nested
    class FindById {

        @DisplayName("존재하는 orderId로 조회하면 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenOrderExists() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, VALID_STOCK));
            Order order = saveOrder(USER_ID, product, brand.getName());

            // act
            Order result = orderService.findById(order.getId());

            // assert
            assertThat(result.getId()).isEqualTo(order.getId());
            assertThat(result.getUserId()).isEqualTo(USER_ID);
        }

        @DisplayName("존재하지 않는 orderId로 조회하면 NOT_FOUND 에러가 발생한다.")
        @Test
        void throwsNotFound_whenOrderDoesNotExist() {
            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> orderService.findById(NOT_EXISTED_ORDER_ID));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("회원 주문 목록 조회 시")
    @Nested
    class FindAllByUserId {

        @DisplayName("기간 내 자신의 주문만 반환한다.")
        @Test
        void returnsOnlyOwnOrdersWithinDateRange() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, VALID_STOCK));

            saveOrder(USER_ID, product, brand.getName());
            saveOrder(OTHER_USER_ID, product, brand.getName()); // 타인의 주문

            ZonedDateTime from = ZonedDateTime.now().minusDays(1);
            ZonedDateTime to = ZonedDateTime.now().plusDays(1);

            // act
            List<Order> result = orderService.findAllByUserId(USER_ID, from, to);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(USER_ID);
        }

        @DisplayName("기간 밖의 주문은 포함되지 않는다.")
        @Test
        void excludesOrdersOutsideDateRange() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, VALID_STOCK));
            saveOrder(USER_ID, product, brand.getName());

            // 미래 기간으로 조회
            ZonedDateTime from = ZonedDateTime.now().plusDays(1);
            ZonedDateTime to = ZonedDateTime.now().plusDays(2);

            // act
            List<Order> result = orderService.findAllByUserId(USER_ID, from, to);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("전체 주문 목록 조회 (관리자) 시")
    @Nested
    class FindAll {

        @DisplayName("전체 주문을 페이지 단위로 반환한다.")
        @Test
        void returnsAllOrdersWithPaging() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, VALID_STOCK));

            saveOrder(USER_ID, product, brand.getName());
            saveOrder(OTHER_USER_ID, product, brand.getName());

            // act
            Page<Order> result = orderService.findAll(PageRequest.of(0, 20));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }
}
