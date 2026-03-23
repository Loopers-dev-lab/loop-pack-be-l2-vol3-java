package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.PgPaymentInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentFacadeTest {

    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private PaymentRepository paymentRepository;
    private PgClient pgClient;
    private PasswordEncryptor passwordEncryptor;
    private PaymentTxService paymentTxService;
    private PaymentFacade paymentFacade;

    private final String loginId = "testuser";
    private final String rawPassword = "TestPass1!";
    private final Long userId = 1L;
    private final Long orderId = 100L;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        orderRepository = mock(OrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        pgClient = mock(PgClient.class);
        passwordEncryptor = mock(PasswordEncryptor.class);
        paymentTxService = mock(PaymentTxService.class);

        paymentFacade = new PaymentFacade(
            userRepository, orderRepository, paymentRepository,
            pgClient, passwordEncryptor, paymentTxService
        );
    }

    @DisplayName("pay: 정상 흐름 - PG 요청 성공 시 PENDING 상태의 Payment를 반환한다")
    @Test
    void pay_success_pgRequestAccepted() {
        mockUser(userId, loginId, rawPassword);
        Order order = createOrder(userId, orderId, BigDecimal.valueOf(5000));
        Payment createdPayment = mockPayment(orderId, userId, PaymentStatus.PENDING);

        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        // 첫 번째 호출(중복 검사): empty, 두 번째 호출(최종 반환): 생성된 결제
        when(paymentRepository.findByOrderId(orderId))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(createdPayment));
        when(paymentTxService.createPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(createdPayment);
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:abc123"));

        PaymentDto.PaymentInfo result = paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678-9814-1451"));

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentTxService).assignTransactionId(anyLong(), eq("20250816:TR:abc123"));
    }

    @DisplayName("pay: 존재하지 않는 사용자면 예외가 발생한다")
    @Test
    void pay_fail_userNotFound() {
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678")))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.USER_NOT_FOUND));

        verify(paymentTxService, never()).createPayment(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @DisplayName("pay: 비밀번호가 틀리면 예외가 발생한다")
    @Test
    void pay_fail_invalidPassword() {
        User user = mock(User.class);
        when(user.getPassword()).thenReturn("encoded");
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(passwordEncryptor.matches(rawPassword, "encoded")).thenReturn(false);

        assertThatThrownBy(() -> paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678")))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.INVALID_PASSWORD));
    }

    @DisplayName("pay: 주문이 없으면 예외가 발생한다")
    @Test
    void pay_fail_orderNotFound() {
        mockUser(userId, loginId, rawPassword);
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678")))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.ORDER_NOT_FOUND));
    }

    @DisplayName("pay: PENDING이 아닌 주문이면 예외가 발생한다")
    @Test
    void pay_fail_orderNotPending() {
        mockUser(userId, loginId, rawPassword);
        Order order = createOrder(userId, orderId, BigDecimal.valueOf(5000));
        order.changeStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678")))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.ORDER_INVALID_STATUS_TRANSITION));
    }

    @DisplayName("pay: 이미 SUCCESS 결제가 존재하면 예외가 발생한다")
    @Test
    void pay_fail_alreadyPaid() {
        mockUser(userId, loginId, rawPassword);
        Order order = createOrder(userId, orderId, BigDecimal.valueOf(5000));
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        Payment successPayment = mockPayment(orderId, userId, PaymentStatus.SUCCESS);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(successPayment));

        assertThatThrownBy(() -> paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678")))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PAYMENT_ALREADY_PAID));

        verify(paymentTxService, never()).createPayment(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @DisplayName("pay: PENDING 결제가 이미 존재하면 새 결제 없이 기존 결제를 반환한다 (멱등)")
    @Test
    void pay_idempotent_whenPendingExists() {
        mockUser(userId, loginId, rawPassword);
        Order order = createOrder(userId, orderId, BigDecimal.valueOf(5000));
        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        Payment pendingPayment = mockPayment(orderId, userId, PaymentStatus.PENDING);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(pendingPayment));

        PaymentDto.PaymentInfo result = paymentFacade.pay(loginId, rawPassword,
            new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678"));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentTxService, never()).createPayment(anyLong(), anyLong(), anyString(), anyString(), any());
        verify(pgClient, never()).requestPayment(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @DisplayName("pay: PG 호출 실패 시 상태 조회로 복구를 시도한다")
    @Test
    void pay_pgFailed_triesStatusCheck() {
        mockUser(userId, loginId, rawPassword);
        Order order = createOrder(userId, orderId, BigDecimal.valueOf(5000));
        Payment createdPayment = mockPayment(orderId, userId, PaymentStatus.PENDING);

        when(orderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(orderId))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(createdPayment));
        when(paymentTxService.createPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(createdPayment);
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.empty()); // PG 호출 실패
        when(pgClient.getPaymentByOrderId(anyLong(), anyLong()))
            .thenReturn(Optional.of(new PgPaymentInfo("txId", String.valueOf(orderId), "SUCCESS", 5000L)));

        paymentFacade.pay(loginId, rawPassword, new PaymentDto.PayCommand(orderId, "SAMSUNG", "1234-5678"));

        verify(pgClient).getPaymentByOrderId(userId, orderId);
        verify(paymentTxService).processPaymentResult(anyLong(), eq(orderId), eq("txId"), eq("SUCCESS"));
    }

    @DisplayName("handleCallback: SUCCESS 콜백이 오면 processPaymentResult를 호출한다")
    @Test
    void handleCallback_success() {
        Payment payment = mockPayment(orderId, userId, PaymentStatus.PENDING);
        when(paymentRepository.findByPgTransactionId("txId123")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        paymentFacade.handleCallback(new PaymentDto.CallbackCommand("txId123", String.valueOf(orderId), "SUCCESS"));

        verify(paymentTxService).processPaymentResult(anyLong(), eq(orderId), eq("txId123"), eq("SUCCESS"));
    }

    @DisplayName("handleCallback: 이미 처리된 결제는 processPaymentResult를 호출하지 않는다 (멱등)")
    @Test
    void handleCallback_idempotent_whenAlreadyProcessed() {
        Payment payment = mockPayment(orderId, userId, PaymentStatus.SUCCESS);
        when(paymentRepository.findByPgTransactionId("txId123")).thenReturn(Optional.of(payment));

        paymentFacade.handleCallback(new PaymentDto.CallbackCommand("txId123", String.valueOf(orderId), "SUCCESS"));

        verify(paymentTxService, never()).processPaymentResult(anyLong(), anyLong(), anyString(), anyString());
    }

    @DisplayName("syncPayment: PENDING 결제를 PG 조회 후 결과를 반영한다")
    @Test
    void syncPayment_success() {
        Payment payment = mockPayment(orderId, userId, PaymentStatus.PENDING);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        when(pgClient.getPaymentByOrderId(userId, orderId))
            .thenReturn(Optional.of(new PgPaymentInfo("txId", String.valueOf(orderId), "SUCCESS", 5000L)));

        paymentFacade.syncPayment(orderId);

        verify(paymentTxService).processPaymentResult(anyLong(), eq(orderId), eq("txId"), eq("SUCCESS"));
    }

    @DisplayName("syncPayment: 이미 SUCCESS인 결제는 PG 조회 없이 그대로 반환한다")
    @Test
    void syncPayment_skip_whenAlreadySuccess() {
        Payment payment = mockPayment(orderId, userId, PaymentStatus.SUCCESS);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        paymentFacade.syncPayment(orderId);

        verify(pgClient, never()).getPaymentByOrderId(anyLong(), anyLong());
        verify(paymentTxService, never()).processPaymentResult(anyLong(), anyLong(), anyString(), anyString());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private User mockUser(Long userId, String loginId, String rawPassword) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getPassword()).thenReturn("encoded");
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(passwordEncryptor.matches(rawPassword, "encoded")).thenReturn(true);
        return user;
    }

    private Order createOrder(Long userId, Long orderId, BigDecimal price) {
        OrderItem item = OrderItem.createSnapshot(10L, "테스트상품", price, 1);
        Order order = Order.create(userId, List.of(item));
        return order;
    }

    private Payment mockPayment(Long orderId, Long userId, PaymentStatus status) {
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(1L);
        when(payment.getOrderId()).thenReturn(orderId);
        when(payment.getUserId()).thenReturn(userId);
        when(payment.getCardType()).thenReturn("SAMSUNG");
        when(payment.getCardNo()).thenReturn("1234-5678-9814-1451");
        when(payment.getAmount()).thenReturn(BigDecimal.valueOf(5000));
        when(payment.getStatus()).thenReturn(status);
        when(payment.getPgTransactionId()).thenReturn(null);
        return payment;
    }
}
