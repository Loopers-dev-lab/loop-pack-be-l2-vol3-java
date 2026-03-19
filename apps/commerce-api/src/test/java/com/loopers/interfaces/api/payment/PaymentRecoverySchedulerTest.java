package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRecoveryScheduler 단위 테스트")
class PaymentRecoverySchedulerTest {

    @Mock
    private PaymentFacade paymentFacade;

    @InjectMocks
    private PaymentRecoveryScheduler scheduler;

    @Test
    @DisplayName("성공: 스케줄러가 PaymentFacade의 복구 메서드를 호출한다")
    void recoverPendingPayments_callsFacade() {
        // When
        scheduler.recoverPendingPayments();

        // Then
        then(paymentFacade).should().recoverPendingPayments();
    }
}
