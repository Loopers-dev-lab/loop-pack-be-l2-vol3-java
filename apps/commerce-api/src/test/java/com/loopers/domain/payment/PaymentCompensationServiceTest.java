package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCompensationService 단위 테스트")
class PaymentCompensationServiceTest {

    @Mock PaymentCompensationRepository compensationRepository;

    @InjectMocks PaymentCompensationService compensationService;

    @Test
    @DisplayName("보정 기록이 없으면 새로 저장한다")
    void recordFailedCommit_ShouldSaveCompensation() {
        when(compensationRepository.existsByPaymentId(1L)).thenReturn(false);

        compensationService.recordFailedCommit(1L, 100L, "stock 삭제됨");

        verify(compensationRepository).save(any(PaymentCompensationModel.class));
    }

    @Test
    @DisplayName("이미 보정 기록이 존재하면 멱등 처리한다")
    void recordFailedCommit_WithExisting_ShouldBeIdempotent() {
        when(compensationRepository.existsByPaymentId(1L)).thenReturn(true);

        compensationService.recordFailedCommit(1L, 100L, "stock 삭제됨");

        verify(compensationRepository, never()).save(any());
    }
}
