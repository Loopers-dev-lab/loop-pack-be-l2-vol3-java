package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Nested
    @DisplayName("createPayment - 결제 생성")
    class CreatePayment {

        @Test
        @DisplayName("성공: 결제를 생성하고 저장한다")
        void createPayment_success() {
            // Given
            Long orderId = 1L;
            Long userId = 1L;
            BigDecimal amount = new BigDecimal("50000");
            CardType cardType = CardType.SAMSUNG;
            String cardNo = "1234-5678-9012-3456";

            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            Payment result = paymentService.createPayment(orderId, userId, amount, cardType, cardNo);

            // Then
            assertThat(result.getOrderId()).isEqualTo(orderId);
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getAmount()).isEqualByComparingTo(amount);
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            then(paymentRepository).should().save(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("getById - 결제 조회")
    class GetById {

        @Test
        @DisplayName("성공: 결제 ID로 조회한다")
        void getById_success() {
            // Given
            Long paymentId = 1L;
            Payment payment = Payment.create(1L, 1L, new BigDecimal("10000"), CardType.KB, "1234");
            given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

            // When
            Payment result = paymentService.getById(paymentId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 결제 ID로 조회하면 NOT_FOUND 예외를 던진다")
        void getById_notFound() {
            // Given
            given(paymentRepository.findById(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> paymentService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getByOrderId - 주문별 결제 조회")
    class GetByOrderId {

        @Test
        @DisplayName("성공: 주문 ID로 결제를 조회한다")
        void getByOrderId_success() {
            // Given
            Long orderId = 1L;
            Payment payment = Payment.create(orderId, 1L, new BigDecimal("10000"), CardType.SAMSUNG, "1234");
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(payment));

            // When
            Payment result = paymentService.getByOrderId(orderId);

            // Then
            assertThat(result.getOrderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("실패: 결제가 없는 주문 ID로 조회하면 NOT_FOUND 예외를 던진다")
        void getByOrderId_notFound() {
            // Given
            given(paymentRepository.findByOrderId(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> paymentService.getByOrderId(999L))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
        }
    }
}
