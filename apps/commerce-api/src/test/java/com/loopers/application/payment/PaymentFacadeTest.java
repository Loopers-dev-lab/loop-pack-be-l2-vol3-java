package com.loopers.application.payment;

import com.loopers.application.payment.dto.CreatePaymentReqDto;
import com.loopers.application.payment.dto.FindPaymentResDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentStatus;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.service.PaymentService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeTest {

    @InjectMocks
    private PaymentFacade paymentFacade;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private MemberService memberService;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderProductService orderProductService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private static Member createTestMember(Long id) {
        return Member.reconstruct(id, "testuser", "encodedPw", "홍길동", LocalDate.of(1990, 1, 1), "test@test.com");
    }

    private static Orders createTestOrder(Long memberId) {
        OrderProduct op = OrderProduct.create(1L, "상품A", 10000, 2);
        return Orders.reconstruct(1L, "ORD-001", memberId, 20000, 0, null, OrderStatus.CREATED, List.of(op));
    }

    private static Payment createTestPayment(Long memberId) {
        return Payment.reconstruct(1L, "ORD-001", memberId, null, "VISA", "1234", "20000", PaymentStatus.PENDING, null);
    }

    private static Payment createRequestedPayment(Long memberId) {
        return Payment.reconstruct(1L, "ORD-001", memberId, "txn-123", "VISA", "1234", "20000", PaymentStatus.REQUESTED, null);
    }

    @DisplayName("결제 생성")
    @Nested
    class CreatePayment {

        @DisplayName("정상 결제 생성 시 Payment 생성 + 이벤트 발행")
        @Test
        void createPayment_success() {
            // arrange
            Member member = createTestMember(1L);
            Orders order = createTestOrder(1L);
            Payment payment = createTestPayment(1L);

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(orderService.getOrderByOrderNumber("ORD-001")).thenReturn(order);
            when(orderProductService.findByOrderId(1L)).thenReturn(order.getOrderProducts());
            when(paymentService.createPaymentWithSnapshots(any(PaymentCommand.Create.class), any())).thenReturn(payment);

            CreatePaymentReqDto dto = new CreatePaymentReqDto("ORD-001", "VISA", "1234");

            // act
            FindPaymentResDto result = paymentFacade.createPayment("testuser", "password", dto);

            // assert
            assertThat(result).isNotNull();
            verify(paymentService).createPaymentWithSnapshots(any(), any());
            verify(eventPublisher, atLeastOnce()).publishEvent(any(Object.class));
        }

        @DisplayName("다른 사람의 주문으로 결제 시도 시 NOT_FOUND")
        @Test
        void createPayment_ownershipFail() {
            // arrange
            Member member = createTestMember(2L); // memberId = 2
            Orders order = createTestOrder(1L);    // order.memberId = 1

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(orderService.getOrderByOrderNumber("ORD-001")).thenReturn(order);

            CreatePaymentReqDto dto = new CreatePaymentReqDto("ORD-001", "VISA", "1234");

            // act & assert
            assertThatThrownBy(() -> paymentFacade.createPayment("testuser", "password", dto))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @DisplayName("결제 콜백")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백 시 결제 성공 + 주문 PAID + 이벤트 발행")
        @Test
        void handleCallback_success() {
            // arrange
            Payment payment = createRequestedPayment(1L);
            Orders order = createTestOrder(1L);

            when(paymentService.getPaymentByTransactionKey("txn-123")).thenReturn(payment);
            when(orderService.getOrderByOrderNumber("ORD-001")).thenReturn(order);

            // act
            paymentFacade.handleCallback("txn-123", PgPaymentStatus.SUCCESS);

            // assert
            verify(paymentService).markSuccess("txn-123");
            verify(orderService).updateOrderStatus(1L, OrderStatus.PAID);
            verify(eventPublisher, atLeastOnce()).publishEvent(any(Object.class));
        }

        @DisplayName("FAILED 콜백 시 결제 실패 + 주문 PAYMENT_FAILED + 재고 복원")
        @Test
        void handleCallback_failed() {
            // arrange
            Payment payment = createRequestedPayment(1L);
            Orders order = createTestOrder(1L);

            when(paymentService.getPaymentByTransactionKey("txn-123")).thenReturn(payment);
            when(orderService.getOrderByOrderNumber("ORD-001")).thenReturn(order);

            // act
            paymentFacade.handleCallback("txn-123", PgPaymentStatus.FAILED);

            // assert
            verify(paymentService).markFailed("txn-123", "FAILED");
            verify(orderService).updateOrderStatus(1L, OrderStatus.PAYMENT_FAILED);
            verify(paymentService).restoreStock(1L);
            verify(eventPublisher, atLeastOnce()).publishEvent(any(Object.class));
        }
    }

    @DisplayName("결제 상태 확인")
    @Nested
    class CheckPaymentStatus {

        @DisplayName("소유자 검증 실패 시 NOT_FOUND")
        @Test
        void checkPaymentStatus_ownershipFail() {
            // arrange
            Member member = createTestMember(2L);
            Payment payment = createRequestedPayment(1L); // memberId = 1

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(paymentService.getPaymentByOrderId("ORD-001")).thenReturn(payment);

            // act & assert
            assertThatThrownBy(() -> paymentFacade.checkPaymentStatus("testuser", "password", "ORD-001"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("PG SUCCESS 시 handleCallback 재사용으로 PAID 처리")
        @Test
        void checkPaymentStatus_pgSuccess() {
            // arrange
            Member member = createTestMember(1L);
            Payment payment = createRequestedPayment(1L);
            Orders order = createTestOrder(1L);

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(paymentService.getPaymentByOrderId("ORD-001")).thenReturn(payment);
            when(paymentGateway.getPaymentByOrderId(1L, "ORD-001"))
                    .thenReturn(new PaymentInfo("txn-123", "ORD-001", "VISA", "1234", "20000", PgPaymentStatus.SUCCESS));
            when(paymentService.getPaymentByTransactionKey("txn-123")).thenReturn(payment);
            when(orderService.getOrderByOrderNumber("ORD-001")).thenReturn(order);

            // act
            paymentFacade.checkPaymentStatus("testuser", "password", "ORD-001");

            // assert
            verify(paymentService).markSuccess("txn-123");
            verify(orderService).updateOrderStatus(1L, OrderStatus.PAID);
        }

        @DisplayName("PG PENDING 시 상태 변경 없음")
        @Test
        void checkPaymentStatus_pgPending() {
            // arrange
            Member member = createTestMember(1L);
            Payment payment = createRequestedPayment(1L);

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(paymentService.getPaymentByOrderId("ORD-001")).thenReturn(payment);
            when(paymentGateway.getPaymentByOrderId(1L, "ORD-001"))
                    .thenReturn(new PaymentInfo("txn-123", "ORD-001", "VISA", "1234", "20000", PgPaymentStatus.PENDING));

            // act
            paymentFacade.checkPaymentStatus("testuser", "password", "ORD-001");

            // assert — handleCallback 호출되지 않음
            verify(paymentService, never()).markSuccess(any());
            verify(paymentService, never()).markFailed(any(), any());
        }
    }
}
