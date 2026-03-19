package com.loopers.application.payment;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResponse;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
public class PaymentFacadeIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Money PRODUCT_PRICE = new Money(10000);
    private static final int INITIAL_STOCK = 10;
    private static final int ORDER_QUANTITY = 2;
    private static final String CARD_TYPE = "SAMSUNG";
    private static final String CARD_NO = "4111-1111-1111-1111";
    private static final String PG_TRANSACTION_KEY = "20260319:TR:abc123";

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PgClient pgClient;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand savedBrand() {
        return brandJpaRepository.save(new Brand("나이키"));
    }

    private Product savedProduct(Long brandId) {
        return productJpaRepository.save(
                new Product(brandId, "나이키 에어맥스", PRODUCT_PRICE, new Stock(INITIAL_STOCK)));
    }

    private Order savedOrder(Long userId, Product product) {
        List<OrderItem> items = List.of(
                new OrderItem(product.getId(), new Quantity(ORDER_QUANTITY),
                        product.getName(), "나이키", product.getPrice())
        );
        Order order = new Order(userId, items, null,
                new Money(PRODUCT_PRICE.getAmount() * ORDER_QUANTITY), new Money(0));
        return orderJpaRepository.save(order);
    }

    @Nested
    @DisplayName("결제 요청")
    class RequestPayment {

        @Test
        @DisplayName("성공: Payment가 PENDING 상태로 생성되고 주문 금액이 정확하다")
        void success() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);

            given(pgClient.requestPayment(any(Long.class), any(PgPaymentRequest.class)))
                    .willReturn(new PgPaymentResponse(PG_TRANSACTION_KEY, true, null, false));

            PaymentCommand command = new PaymentCommand(order.getId(), CARD_TYPE, CARD_NO);

            // act
            PaymentInfo result = paymentFacade.requestPayment(USER_ID, command);

            // assert — Facade 반환값은 PENDING (PG 호출은 AFTER_COMMIT에서 비동기 처리)
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.orderId()).isEqualTo(order.getId());
            assertThat(result.amount()).isEqualTo(PRODUCT_PRICE.getAmount() * ORDER_QUANTITY);
            assertThat(result.cardType()).isEqualTo(CARD_TYPE);
            assertThat(result.cardNo()).isEqualTo(CARD_NO);
        }

        @Test
        @DisplayName("실패: 타인의 주문에 결제 요청 시 NOT_FOUND 예외")
        void fail_otherUserOrder() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);

            PaymentCommand command = new PaymentCommand(order.getId(), CARD_TYPE, CARD_NO);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(OTHER_USER_ID, command))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("존재하지 않는 주문");
        }

        @Test
        @DisplayName("실패: PENDING_PAYMENT가 아닌 주문에 결제 요청 시 BAD_REQUEST 예외")
        void fail_notPendingPayment() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);
            order.markPaid();
            orderJpaRepository.save(order);

            PaymentCommand command = new PaymentCommand(order.getId(), CARD_TYPE, CARD_NO);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, command))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("결제 대기 상태의 주문만");
        }

        @Test
        @DisplayName("실패: 이미 결제가 존재하는 주문에 중복 결제 요청 시 CONFLICT 예외")
        void fail_duplicatePayment() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);

            given(pgClient.requestPayment(any(Long.class), any(PgPaymentRequest.class)))
                    .willReturn(new PgPaymentResponse(PG_TRANSACTION_KEY, true, null, false));

            PaymentCommand command = new PaymentCommand(order.getId(), CARD_TYPE, CARD_NO);
            paymentFacade.requestPayment(USER_ID, command);

            // act & assert — 두 번째 결제 요청은 앱 레벨에서 CONFLICT
            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, command))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이미 결제가 진행 중");
        }
    }

    @Nested
    @DisplayName("결제 상태 조회")
    class FindByOrderId {

        @Test
        @DisplayName("성공: 본인 주문의 결제 상태를 조회한다")
        void success() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);

            given(pgClient.requestPayment(any(Long.class), any(PgPaymentRequest.class)))
                    .willReturn(new PgPaymentResponse(PG_TRANSACTION_KEY, true, null, false));

            PaymentCommand command = new PaymentCommand(order.getId(), CARD_TYPE, CARD_NO);
            paymentFacade.requestPayment(USER_ID, command);

            // act
            PaymentInfo result = paymentFacade.findByOrderId(order.getId(), USER_ID);

            // assert
            assertThat(result.orderId()).isEqualTo(order.getId());
            assertThat(result.amount()).isEqualTo(PRODUCT_PRICE.getAmount() * ORDER_QUANTITY);
        }

        @Test
        @DisplayName("실패: 타인 주문의 결제 상태 조회 시 NOT_FOUND 예외")
        void fail_otherUserOrder() {
            // arrange
            Brand brand = savedBrand();
            Product product = savedProduct(brand.getId());
            Order order = savedOrder(USER_ID, product);

            given(pgClient.requestPayment(any(Long.class), any(PgPaymentRequest.class)))
                    .willReturn(new PgPaymentResponse(PG_TRANSACTION_KEY, true, null, false));

            PaymentCommand command = new PaymentCommand(order.getId(), CARD_TYPE, CARD_NO);
            paymentFacade.requestPayment(USER_ID, command);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.findByOrderId(order.getId(), OTHER_USER_ID))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("존재하지 않는 주문");
        }
    }
}
