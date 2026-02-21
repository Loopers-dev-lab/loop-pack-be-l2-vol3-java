package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PaymentErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = Mockito.mock(PaymentRepository.class);
        paymentService = new PaymentService(paymentRepository);
    }

    @DisplayName("결제를 생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_REQUESTED_상태로_생성된다() {
            // arrange
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Payment payment = paymentService.create(1L, 50000, "CARD", "IDEM-001");

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            paymentService.create(1L, 50000, "CARD", "IDEM-001");

            // assert
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    @DisplayName("결제를 승인할 때,")
    @Nested
    class 승인 {

        @Test
        void 존재하지_않는_결제면_예외가_발생한다() {
            // arrange
            when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> paymentService.approve(1L, "PG-TXN-001", 50000))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PaymentErrorType.PAYMENT_NOT_FOUND);
        }

        @Test
        void 유효한_요청이면_approve가_호출된다() {
            // arrange
            Payment payment = Payment.create(1L, 50000, "CARD", "IDEM-001");
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            // act
            paymentService.approve(1L, "PG-TXN-001", 50000);

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        }
    }

    @DisplayName("결제를 실패처리할 때,")
    @Nested
    class 실패처리 {

        @Test
        void 존재하지_않는_결제면_예외가_발생한다() {
            // arrange
            when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> paymentService.fail(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PaymentErrorType.PAYMENT_NOT_FOUND);
        }

        @Test
        void 유효한_요청이면_fail이_호출된다() {
            // arrange
            Payment payment = Payment.create(1L, 50000, "CARD", "IDEM-001");
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            // act
            paymentService.fail(1L);

            // assert
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }
}
