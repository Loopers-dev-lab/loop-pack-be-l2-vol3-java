package com.loopers.domain.payment;

import com.loopers.support.enums.CardType;
import com.loopers.support.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 도메인 서비스 테스트")
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;

    @InjectMocks PaymentService paymentService;

    @Test
    @DisplayName("createPayment 정상 호출 시 REQUESTED 상태의 결제가 생성된다")
    void createPayment_WithValidInput_ShouldCreatePayment() {
        when(paymentRepository.save(any(PaymentModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentModel result = paymentService.createPayment(
                1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", BigDecimal.valueOf(50000)
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(100L);
        verify(paymentRepository).save(any(PaymentModel.class));
    }

    @Test
    @DisplayName("completePayment CAS 성공 시 true를 반환한다")
    void completePayment_WithCasSuccess_ShouldReturnTrue() {
        Long paymentId = 10L;

        when(paymentRepository.casUpdateStatus(paymentId, PaymentStatus.REQUESTED, PaymentStatus.SUCCESS, null))
                .thenReturn(1);

        boolean result = paymentService.completePayment(paymentId, PaymentStatus.SUCCESS, null);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("completePayment CAS 실패 시 false를 반환한다 (멱등)")
    void completePayment_WithCasFail_ShouldReturnFalse() {
        Long paymentId = 10L;

        when(paymentRepository.casUpdateStatus(paymentId, PaymentStatus.REQUESTED, PaymentStatus.SUCCESS, null))
                .thenReturn(0);

        boolean result = paymentService.completePayment(paymentId, PaymentStatus.SUCCESS, null);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("completePayment FAILED 전이 시 실패 사유가 CAS 쿼리로 전달된다")
    void completePayment_WithFailedStatus_ShouldPassReasonToCas() {
        Long paymentId = 10L;

        when(paymentRepository.casUpdateStatus(paymentId, PaymentStatus.REQUESTED, PaymentStatus.FAILED, "잔액 부족"))
                .thenReturn(1);

        boolean result = paymentService.completePayment(paymentId, PaymentStatus.FAILED, "잔액 부족");

        assertThat(result).isTrue();
        verify(paymentRepository).casUpdateStatus(paymentId, PaymentStatus.REQUESTED, PaymentStatus.FAILED, "잔액 부족");
    }

    @Test
    @DisplayName("hasActivePayment REQUESTED 존재 시 true를 반환한다")
    void hasActivePayment_WithRequestedPayment_ShouldReturnTrue() {
        when(paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.REQUESTED))
                .thenReturn(true);

        assertThat(paymentService.hasActivePayment(1L)).isTrue();
    }

    @Test
    @DisplayName("hasActivePayment REQUESTED 없으면 false를 반환한다")
    void hasActivePayment_WithNoRequestedPayment_ShouldReturnFalse() {
        when(paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.REQUESTED))
                .thenReturn(false);

        assertThat(paymentService.hasActivePayment(1L)).isFalse();
    }

    @Test
    @DisplayName("findRequestedBefore 지정 시간 이전 REQUESTED 결제를 조회한다")
    void findRequestedBefore_ShouldDelegateToRepository() {
        LocalDateTime before = LocalDateTime.of(2026, 3, 18, 12, 0);
        PaymentModel payment = PaymentModel.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", BigDecimal.valueOf(10000));

        when(paymentRepository.findAllRequestedBeforeMinutesAgo(1))
                .thenReturn(List.of(payment));

        List<PaymentModel> result = paymentService.findRequestedBeforeMinutesAgo(1);

        assertThat(result).hasSize(1);
        verify(paymentRepository).findAllRequestedBeforeMinutesAgo(1);
    }
}
