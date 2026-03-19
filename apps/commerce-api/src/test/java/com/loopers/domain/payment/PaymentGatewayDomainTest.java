package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentGateway 도메인 포트 테스트")
class PaymentGatewayDomainTest {

    @Test
    @DisplayName("유효한 결제 요청 모델은 생성된다")
    void request_ValidInput_CreatesModel() {
        PaymentGateway.PaymentGatewayRequest request = new PaymentGateway.PaymentGatewayRequest(
                "member-1",
                "ORDER-001",
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                5000,
                "http://localhost:8080/api/v1/payments/callback"
        );

        assertThat(request.memberId()).isEqualTo("member-1");
        assertThat(request.orderReference()).isEqualTo("ORDER-001");
        assertThat(request.amount()).isEqualTo(5000);
    }

    @Test
    @DisplayName("결제 요청 금액이 0 이하면 BAD_REQUEST 예외가 발생한다")
    void request_AmountNotPositive_ThrowsBadRequest() {
        assertThatThrownBy(() -> new PaymentGateway.PaymentGatewayRequest(
                "member-1",
                "ORDER-001",
                CardType.SAMSUNG,
                "1234-5678-1234-5678",
                0,
                "http://localhost:8080/api/v1/payments/callback"
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("취소 요청 거래 키가 비어 있으면 BAD_REQUEST 예외가 발생한다")
    void cancelRequest_BlankTransactionKey_ThrowsBadRequest() {
        assertThatThrownBy(() -> new PaymentGateway.PaymentGatewayCancelRequest("member-1", " "))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("실패 상태 트랜잭션에 사유가 없으면 BAD_REQUEST 예외가 발생한다")
    void transaction_FailedWithoutReason_ThrowsBadRequest() {
        assertThatThrownBy(() -> new PaymentGateway.PaymentGatewayTransaction(
                "TRX-1",
                "ORDER-001",
                PaymentStatus.FAILED,
                null
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("PaymentRepository 인터페이스가 존재한다")
    void paymentRepositoryInterfaceExists() {
        assertThat(PaymentRepository.class).isNotNull();
    }
}
