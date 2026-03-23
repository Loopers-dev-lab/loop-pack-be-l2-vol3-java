package com.loopers.application.payment;

import com.loopers.application.user.UserDto;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.PgPaymentInfo;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private com.loopers.application.order.OrderFacade orderFacade;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PgClient pgClient;

    private Long userId;
    private Long orderId;
    private String loginId = "payuser1";
    private String rawPassword = "TestPass1!";

    @BeforeEach
    void setUp() {
        UserDto.UserInfo user = userFacade.register(
            loginId, rawPassword, "결제테스터",
            LocalDate.of(2000, 1, 1), "pay1@loopers.com",
            com.loopers.domain.user.Gender.MALE
        );
        userId = user.id();

        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("PAY_BRAND"), new BrandDescription("결제 브랜드"))
        );
        Long productId = productJpaRepository.save(
            Product.create(brand.getId(), "결제 상품", "설명", BigDecimal.valueOf(10000), 5)
        ).getId();

        com.loopers.application.order.OrderDto.OrderInfo order = orderFacade.placeOrder(
            userId,
            java.util.List.of(new com.loopers.application.order.OrderDto.OrderLineCommand(productId, 1)),
            null
        );
        orderId = order.id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("pay: PG 요청 성공 시 Payment(PENDING)가 DB에 저장된다")
    @Test
    void pay_success_paymentSavedAsPending() {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:test01"));

        PaymentDto.PaymentInfo result = paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.pgTransactionId()).isEqualTo("20250816:TR:test01");
        assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
    }

    @DisplayName("pay: PENDING 결제가 이미 있으면 새로 생성하지 않는다 (멱등)")
    @Test
    void pay_idempotent_noDuplicatePayment() {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:test02"));

        paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));
        PaymentDto.PaymentInfo second = paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));

        assertThat(second.status()).isEqualTo(PaymentStatus.PENDING);
        // PG 요청은 첫 번째 호출에만 발생
        org.mockito.Mockito.verify(pgClient, org.mockito.Mockito.times(1))
            .requestPayment(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @DisplayName("handleCallback: SUCCESS 콜백 수신 시 Order가 CONFIRMED로 변경된다")
    @Test
    void handleCallback_success_orderConfirmed() {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:test03"));

        paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));

        paymentFacade.handleCallback(
            new PaymentDto.CallbackCommand("20250816:TR:test03", String.valueOf(orderId), "SUCCESS")
        );

        PaymentDto.PaymentInfo payment = paymentFacade.getPayment(orderId);
        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);

        com.loopers.application.order.OrderDto.OrderInfo order = orderFacade.getOrder(userId, orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @DisplayName("handleCallback: FAILED 콜백 수신 시 Order가 CANCELLED되고 재고가 복구된다")
    @Test
    void handleCallback_failed_orderCancelledAndStockRestored() {
        Long productId = orderFacade.getOrder(userId, orderId).items().getFirst().productId();
        // setUp에서 수량=1 주문 → stock=4. FAILED 후 복구되면 stock=5로 되어야 함
        int stockBeforePayment = productRepository.findById(productId).orElseThrow().getStock(); // 4

        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:test04"));

        paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));

        paymentFacade.handleCallback(
            new PaymentDto.CallbackCommand("20250816:TR:test04", String.valueOf(orderId), "FAILED")
        );

        PaymentDto.PaymentInfo payment = paymentFacade.getPayment(orderId);
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);

        com.loopers.application.order.OrderDto.OrderInfo order = orderFacade.getOrder(userId, orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);

        int stockAfter = productRepository.findById(productId).orElseThrow().getStock();
        // 주문 수량(1)만큼 재고가 복구되어야 한다
        assertThat(stockAfter).isEqualTo(stockBeforePayment + 1);
    }

    @DisplayName("handleCallback: 이미 SUCCESS 처리된 결제에 중복 콜백이 와도 멱등하게 처리된다")
    @Test
    void handleCallback_idempotent_whenAlreadySuccess() {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:test05"));

        paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));
        paymentFacade.handleCallback(
            new PaymentDto.CallbackCommand("20250816:TR:test05", String.valueOf(orderId), "SUCCESS")
        );

        // 중복 콜백
        PaymentDto.PaymentInfo result = paymentFacade.handleCallback(
            new PaymentDto.CallbackCommand("20250816:TR:test05", String.valueOf(orderId), "SUCCESS")
        );

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @DisplayName("syncPayment: PG 조회 성공 시 PENDING 결제가 최종 상태로 반영된다")
    @Test
    void syncPayment_success_pendingResolved() {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.empty()); // PG 요청 실패(타임아웃 등)
        when(pgClient.getPaymentByOrderId(anyLong(), anyLong()))
            .thenReturn(Optional.empty()); // 초기 복구 시도도 실패

        paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));

        // Payment가 PENDING 상태로 저장됨
        assertThat(paymentRepository.findByOrderId(orderId))
            .isPresent()
            .hasValueSatisfying(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING));

        // 이후 관리자가 sync 호출 → PG에서 SUCCESS 확인
        when(pgClient.getPaymentByOrderId(anyLong(), eq(orderId)))
            .thenReturn(Optional.of(new PgPaymentInfo("txId-sync", String.valueOf(orderId), "SUCCESS", 10000L)));

        PaymentDto.PaymentInfo synced = paymentFacade.syncPayment(orderId);

        assertThat(synced.status()).isEqualTo(PaymentStatus.SUCCESS);
        com.loopers.application.order.OrderDto.OrderInfo order = orderFacade.getOrder(userId, orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @DisplayName("pay: 존재하지 않는 사용자로 결제하면 예외가 발생한다")
    @Test
    void pay_fail_userNotFound() {
        assertThatThrownBy(() -> paymentFacade.pay("unknown", "pw",
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678")))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.USER_NOT_FOUND));
    }
}
