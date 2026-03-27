package com.loopers.application.payment;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.*;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeUnitTest {

    @Mock
    private MemberService memberService;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PgPaymentGateway pgPaymentGateway;

    @InjectMocks
    private PaymentFacade paymentFacade;

    private MemberModel createMember() {
        MemberModel member = new MemberModel("testuser", "password1!@", "홍길동",
                LocalDate.of(2000, 6, 5), "test@example.com");
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    private OrderModel createOrder() {
        OrderItemModel item = new OrderItemModel(10L, "에어맥스", "나이키", 129000, 1);
        OrderModel order = new OrderModel(1L, List.of(item), null, 0);
        ReflectionTestUtils.setField(order, "id", 100L);
        return order;
    }

    private PaymentModel createPayment() {
        PaymentModel payment = new PaymentModel(100L, 1L, 129000, "SAMSUNG", "1234-5678-9814-1451");
        ReflectionTestUtils.setField(payment, "id", 1L);
        return payment;
    }

    @DisplayName("결제를 요청할 때,")
    @Nested
    class RequestPayment {

        @DisplayName("PG 요청이 성공하면, PENDING 상태로 transactionId가 저장된다.")
        @Test
        void requestPaymentSuccess() {
            // given
            MemberModel member = createMember();
            OrderModel order = createOrder();
            PaymentModel payment = createPayment();

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(orderService.getById(100L)).thenReturn(order);
            when(paymentService.create(eq(100L), eq(1L), eq(129000), eq("SAMSUNG"), eq("1234-5678-9814-1451")))
                    .thenReturn(payment);

            PgPaymentResponse pgResponse = new PgPaymentResponse(
                    new PgPaymentResponse.Meta("SUCCESS", null, null),
                    new PgPaymentResponse.Data("20260316:TR:abc123", "PENDING")
            );
            when(pgPaymentGateway.requestPayment(eq("1"), any(PgPaymentRequest.class))).thenReturn(pgResponse);

            // 트랜잭션 분리 후 최종 조회 결과
            PaymentModel updatedPayment = createPayment();
            updatedPayment.assignTransactionId("20260316:TR:abc123");
            when(paymentService.getById(1L)).thenReturn(updatedPayment);

            ReflectionTestUtils.setField(paymentFacade, "callbackUrl", "http://localhost:8080/api/v1/payments/callback");

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest("100", "SAMSUNG", "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment("testuser", "password1!@", request);

            // then
            assertAll(
                    () -> assertThat(result.paymentId()).isEqualTo(1L),
                    () -> assertThat(result.status()).isEqualTo("PENDING"),
                    () -> assertThat(result.transactionId()).isEqualTo("20260316:TR:abc123")
            );
            verify(paymentService).assignTransactionId(1L, "20260316:TR:abc123");
        }

        @DisplayName("PG 응답이 FAIL이면, FAILED 상태로 저장된다.")
        @Test
        void requestPaymentPgFail() {
            // given
            MemberModel member = createMember();
            OrderModel order = createOrder();
            PaymentModel payment = createPayment();

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(orderService.getById(100L)).thenReturn(order);
            when(paymentService.create(eq(100L), eq(1L), eq(129000), eq("SAMSUNG"), eq("1234-5678-9814-1451")))
                    .thenReturn(payment);

            PgPaymentResponse pgResponse = new PgPaymentResponse(
                    new PgPaymentResponse.Meta("FAIL", "Internal Server Error", "현재 서버가 불안정합니다."),
                    null
            );
            when(pgPaymentGateway.requestPayment(eq("1"), any(PgPaymentRequest.class))).thenReturn(pgResponse);

            // 트랜잭션 분리 후 최종 조회 결과
            PaymentModel failedPayment = createPayment();
            failedPayment.markFailed("현재 서버가 불안정합니다.");
            when(paymentService.getById(1L)).thenReturn(failedPayment);

            ReflectionTestUtils.setField(paymentFacade, "callbackUrl", "http://localhost:8080/api/v1/payments/callback");

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest("100", "SAMSUNG", "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment("testuser", "password1!@", request);

            // then
            assertThat(result.status()).isEqualTo("FAILED");
            verify(paymentService).markFailed(1L, "현재 서버가 불안정합니다.");
        }

        @DisplayName("PG 장애로 fallback이 실행되면, TIMED_OUT 상태로 저장된다.")
        @Test
        void requestPaymentFallback() {
            // given
            MemberModel member = createMember();
            OrderModel order = createOrder();
            PaymentModel payment = createPayment();

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(orderService.getById(100L)).thenReturn(order);
            when(paymentService.create(eq(100L), eq(1L), eq(129000), eq("SAMSUNG"), eq("1234-5678-9814-1451")))
                    .thenReturn(payment);

            PgPaymentResponse fallbackResponse = new PgPaymentResponse(
                    new PgPaymentResponse.Meta("FAIL", "PG_UNAVAILABLE", "결제 시스템 일시 장애"),
                    null
            );
            when(pgPaymentGateway.requestPayment(eq("1"), any(PgPaymentRequest.class))).thenReturn(fallbackResponse);

            // 트랜잭션 분리 후 최종 조회 결과
            PaymentModel timedOutPayment = createPayment();
            timedOutPayment.markTimedOut(null);
            when(paymentService.getById(1L)).thenReturn(timedOutPayment);

            ReflectionTestUtils.setField(paymentFacade, "callbackUrl", "http://localhost:8080/api/v1/payments/callback");

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest("100", "SAMSUNG", "1234-5678-9814-1451");

            // when
            PaymentInfo result = paymentFacade.requestPayment("testuser", "password1!@", request);

            // then
            assertThat(result.status()).isEqualTo("TIMED_OUT");
            verify(paymentService).markTimedOut(1L, null);
        }

        @DisplayName("다른 회원의 주문이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithOtherMemberOrder() {
            // given
            MemberModel member = createMember();
            OrderModel order = createOrder();
            ReflectionTestUtils.setField(order, "memberId", 99L);

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(orderService.getById(100L)).thenReturn(order);

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest("100", "SAMSUNG", "1234-5678-9814-1451");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentFacade.requestPayment("testuser", "password1!@", request)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundOrder() {
            // given
            MemberModel member = createMember();
            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(orderService.getById(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다."));

            PaymentV1Dto.PaymentRequest request = new PaymentV1Dto.PaymentRequest("999", "SAMSUNG", "1234-5678-9814-1451");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentFacade.requestPayment("testuser", "password1!@", request)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("콜백을 수신할 때,")
    @Nested
    class HandleCallback {

        @DisplayName("성공 콜백이면, SUCCESS 상태로 전이된다.")
        @Test
        void handleCallbackSuccess() {
            // given
            PaymentModel payment = createPayment();
            payment.assignTransactionId("20260316:TR:abc123");
            when(paymentService.getByTransactionId("20260316:TR:abc123")).thenReturn(payment);

            PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest(
                    "20260316:TR:abc123", "100", "SUCCESS", null
            );

            // when
            paymentFacade.handleCallback(callback);

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("실패 콜백이면, FAILED 상태로 전이된다.")
        @Test
        void handleCallbackFailed() {
            // given
            PaymentModel payment = createPayment();
            payment.assignTransactionId("20260316:TR:abc123");
            when(paymentService.getByTransactionId("20260316:TR:abc123")).thenReturn(payment);

            PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest(
                    "20260316:TR:abc123", "100", "FAILED", "한도 초과"
            );

            // when
            paymentFacade.handleCallback(callback);

            // then
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                    () -> assertThat(payment.getFailReason()).isEqualTo("한도 초과")
            );
        }

        @DisplayName("이미 SUCCESS 상태인 결제에 중복 콜백이 오면, 예외가 발생한다.")
        @Test
        void handleCallbackDuplicate() {
            // given
            PaymentModel payment = createPayment();
            payment.assignTransactionId("20260316:TR:abc123");
            payment.markSuccess("20260316:TR:abc123");
            when(paymentService.getByTransactionId("20260316:TR:abc123")).thenReturn(payment);

            PaymentV1Dto.CallbackRequest callback = new PaymentV1Dto.CallbackRequest(
                    "20260316:TR:abc123", "100", "SUCCESS", null
            );

            // when & then
            assertThrows(CoreException.class, () ->
                    paymentFacade.handleCallback(callback)
            );
        }
    }

    @DisplayName("결제 상태를 동기화할 때,")
    @Nested
    class SyncPaymentStatus {

        @DisplayName("이미 SUCCESS 상태이면, PG 조회 없이 그대로 반환한다.")
        @Test
        void syncAlreadySuccess() {
            // given
            MemberModel member = createMember();
            PaymentModel payment = createPayment();
            payment.assignTransactionId("20260316:TR:abc123");
            payment.markSuccess("20260316:TR:abc123");

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(paymentService.getById(1L)).thenReturn(payment);

            // when
            PaymentInfo result = paymentFacade.syncPaymentStatus("testuser", "password1!@", 1L);

            // then
            assertThat(result.status()).isEqualTo("SUCCESS");
        }

        @DisplayName("TIMED_OUT 상태에서 PG 조회 시 SUCCESS이면, SUCCESS로 복구된다.")
        @Test
        void syncTimedOutToSuccess() {
            // given
            MemberModel member = createMember();
            PaymentModel timedOutPayment = createPayment();
            timedOutPayment.markTimedOut("20260316:TR:abc123");

            PaymentModel successPayment = createPayment();
            successPayment.assignTransactionId("20260316:TR:abc123");
            successPayment.markSuccess("20260316:TR:abc123");

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(paymentService.getById(1L))
                    .thenReturn(timedOutPayment)   // 1차: 현재 상태 확인
                    .thenReturn(successPayment);   // 2차: 상태 반영 후 조회

            PgPaymentStatusResponse pgStatus = new PgPaymentStatusResponse(
                    new PgPaymentStatusResponse.Meta("SUCCESS", null, null),
                    new PgPaymentStatusResponse.Data(
                            "20260316:TR:abc123", "100", "SAMSUNG",
                            "1234-5678-9814-1451", 129000L, "SUCCESS", "정상 승인되었습니다."
                    )
            );
            when(pgPaymentGateway.getPaymentStatus("1", "20260316:TR:abc123")).thenReturn(pgStatus);

            // when
            PaymentInfo result = paymentFacade.syncPaymentStatus("testuser", "password1!@", 1L);

            // then
            assertThat(result.status()).isEqualTo("SUCCESS");
            verify(paymentService).markSuccess(1L, "20260316:TR:abc123");
        }

        @DisplayName("TIMED_OUT 상태에서 PG 조회 시 FAILED이면, FAILED로 복구된다.")
        @Test
        void syncTimedOutToFailed() {
            // given
            MemberModel member = createMember();
            PaymentModel timedOutPayment = createPayment();
            timedOutPayment.markTimedOut("20260316:TR:abc123");

            PaymentModel failedPayment = createPayment();
            failedPayment.markFailed("한도 초과");

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(paymentService.getById(1L))
                    .thenReturn(timedOutPayment)
                    .thenReturn(failedPayment);

            PgPaymentStatusResponse pgStatus = new PgPaymentStatusResponse(
                    new PgPaymentStatusResponse.Meta("SUCCESS", null, null),
                    new PgPaymentStatusResponse.Data(
                            "20260316:TR:abc123", "100", "SAMSUNG",
                            "1234-5678-9814-1451", 129000L, "FAILED", "한도 초과"
                    )
            );
            when(pgPaymentGateway.getPaymentStatus("1", "20260316:TR:abc123")).thenReturn(pgStatus);

            // when
            PaymentInfo result = paymentFacade.syncPaymentStatus("testuser", "password1!@", 1L);

            // then
            assertThat(result.status()).isEqualTo("FAILED");
            verify(paymentService).markFailed(1L, "한도 초과");
        }

        @DisplayName("TIMED_OUT 상태에서 transactionId가 없으면, orderId로 PG를 조회하여 복구한다.")
        @Test
        void syncTimedOutWithoutTransactionId() {
            // given
            MemberModel member = createMember();
            PaymentModel timedOutPayment = createPayment();
            timedOutPayment.markTimedOut(null);

            PaymentModel successPayment = createPayment();
            successPayment.assignTransactionId("20260316:TR:recovered");
            successPayment.markSuccess("20260316:TR:recovered");

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(paymentService.getById(1L))
                    .thenReturn(timedOutPayment)
                    .thenReturn(successPayment);

            PgOrderResponse pgOrder = new PgOrderResponse(
                    new PgOrderResponse.Meta("SUCCESS", null, null),
                    new PgOrderResponse.Data("100", List.of(
                            new PgOrderResponse.Transaction("20260316:TR:recovered", "SUCCESS", "정상 승인되었습니다.")
                    ))
            );
            when(pgPaymentGateway.getPaymentByOrderId("1", "100")).thenReturn(pgOrder);

            // when
            PaymentInfo result = paymentFacade.syncPaymentStatus("testuser", "password1!@", 1L);

            // then
            assertAll(
                    () -> assertThat(result.status()).isEqualTo("SUCCESS"),
                    () -> assertThat(result.transactionId()).isEqualTo("20260316:TR:recovered")
            );
            verify(paymentService).markSuccess(1L, "20260316:TR:recovered");
        }

        @DisplayName("다른 회원의 결제이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void failWithOtherMemberPayment() {
            // given
            MemberModel member = createMember();
            PaymentModel payment = createPayment();
            ReflectionTestUtils.setField(payment, "memberId", 99L);

            when(memberService.getMyInfo("testuser", "password1!@")).thenReturn(member);
            when(paymentService.getById(1L)).thenReturn(payment);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentFacade.syncPaymentStatus("testuser", "password1!@", 1L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
