package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @DisplayName("결제를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("PENDING 상태로 저장된다.")
        @Test
        void createSuccess() {
            // given
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(PaymentModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentModel result = paymentService.create(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");

            // then
            assertAll(
                    () -> assertThat(result.getOrderId()).isEqualTo(1L),
                    () -> assertThat(result.getMemberId()).isEqualTo(100L),
                    () -> assertThat(result.getAmount()).isEqualTo(50000),
                    () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING)
            );
            verify(paymentRepository, times(1)).save(any(PaymentModel.class));
        }

        @DisplayName("이미 진행 중인 결제가 있으면, CONFLICT 예외가 발생한다.")
        @Test
        void failWithDuplicatePayment() {
            // given
            PaymentModel existing = new PaymentModel(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(existing));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentService.create(1L, 100L, 50000, "VISA", "4111-1111-1111-1111")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("이전 결제가 FAILED 상태면, 재결제가 가능하다.")
        @Test
        void createAfterFailedPayment() {
            // given
            PaymentModel failed = new PaymentModel(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");
            failed.markFailed("한도 초과");
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(failed));
            when(paymentRepository.save(any(PaymentModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // when
            PaymentModel result = paymentService.create(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");

            // then
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @DisplayName("결제를 ID로 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하면, 결제를 반환한다.")
        @Test
        void getByIdSuccess() {
            // given
            PaymentModel payment = new PaymentModel(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            // when
            PaymentModel result = paymentService.getById(1L);

            // then
            assertThat(result.getOrderId()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundId() {
            // given
            when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentService.getById(999L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제를 거래 ID로 조회할 때,")
    @Nested
    class GetByTransactionId {

        @DisplayName("존재하면, 결제를 반환한다.")
        @Test
        void getByTransactionIdSuccess() {
            // given
            PaymentModel payment = new PaymentModel(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");
            when(paymentRepository.findByTransactionId("txn-001")).thenReturn(Optional.of(payment));

            // when
            PaymentModel result = paymentService.getByTransactionId("txn-001");

            // then
            assertThat(result.getOrderId()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundTransactionId() {
            // given
            when(paymentRepository.findByTransactionId("txn-999")).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentService.getByTransactionId("txn-999")
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("결제를 주문 ID로 조회할 때,")
    @Nested
    class GetByOrderId {

        @DisplayName("존재하면, 결제를 반환한다.")
        @Test
        void getByOrderIdSuccess() {
            // given
            PaymentModel payment = new PaymentModel(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

            // when
            PaymentModel result = paymentService.getByOrderId(1L);

            // then
            assertThat(result.getOrderId()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundOrderId() {
            // given
            when(paymentRepository.findByOrderId(999L)).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentService.getByOrderId(999L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("정체된 결제를 조회할 때,")
    @Nested
    class FindStalledPayments {

        @DisplayName("PENDING, TIMED_OUT 상태의 결제 목록을 반환한다.")
        @Test
        void findStalledPaymentsSuccess() {
            // given
            PaymentModel pending = new PaymentModel(1L, 100L, 50000, "VISA", "4111-1111-1111-1111");
            PaymentModel timedOut = new PaymentModel(2L, 100L, 30000, "MASTER", "5500-0000-0000-0004");
            timedOut.markTimedOut("txn-002");

            when(paymentRepository.findAllByStatusIn(List.of(PaymentStatus.PENDING, PaymentStatus.TIMED_OUT)))
                    .thenReturn(List.of(pending, timedOut));

            // when
            List<PaymentModel> result = paymentService.findStalledPayments();

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> {
                        Assertions.assertNotNull(result);
                        assertThat(result.getFirst().getStatus()).isEqualTo(PaymentStatus.PENDING);
                    },
                    () -> {
                        Assertions.assertNotNull(result);
                        assertThat(result.get(1).getStatus()).isEqualTo(PaymentStatus.TIMED_OUT);
                    }
            );
        }
    }
}
