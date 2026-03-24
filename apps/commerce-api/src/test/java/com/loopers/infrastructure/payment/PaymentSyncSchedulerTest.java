package com.loopers.infrastructure.payment;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class PaymentSyncSchedulerTest {

    @InjectMocks
    private PaymentSyncScheduler scheduler;

    @Mock
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName("스케줄러가 PaymentFacade.syncPendingPayments()를 호출한다")
    void syncPendingPayments_delegates_to_facade() {
        // given
        given(paymentFacade.syncPendingPayments()).willReturn(List.of());

        // when & then
        scheduler.syncPendingPayments();
        then(paymentFacade).should().syncPendingPayments();
    }

    @Test
    @DisplayName("Facade에서 예외 발생 시 스케줄러가 전파한다")
    void syncPendingPayments_propagates_exception() {
        // given
        given(paymentFacade.syncPendingPayments()).willThrow(new RuntimeException("Redis 연결 실패"));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> scheduler.syncPendingPayments());
    }
}
