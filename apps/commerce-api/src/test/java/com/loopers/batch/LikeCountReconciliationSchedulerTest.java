package com.loopers.batch;

import com.loopers.domain.product.LikeCountMismatch;
import com.loopers.domain.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LikeCountReconciliationScheduler 단위 테스트")
class LikeCountReconciliationSchedulerTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    LikeCountReconciliationScheduler scheduler;

    @Test
    @DisplayName("불일치 없으면 updateLikeCount 호출 안 함")
    void reconcile_NoMismatch_ShouldSkip() {
        when(productRepository.findLikeCountMismatches()).thenReturn(List.of());

        scheduler.reconcileLikeCounts();

        verify(productRepository, never()).updateLikeCount(anyLong(), anyLong());
    }

    @Test
    @DisplayName("불일치 감지 시 건별 보정")
    void reconcile_WithMismatch_ShouldFix() {
        when(productRepository.findLikeCountMismatches())
            .thenReturn(List.of(new LikeCountMismatch(1L, 10L, 12L)));

        scheduler.reconcileLikeCounts();

        verify(productRepository).updateLikeCount(1L, 12L);
    }

    @Test
    @DisplayName("보정 중 예외 발생 시 다음 건 계속 처리")
    void reconcile_PartialFailure_ShouldContinue() {
        when(productRepository.findLikeCountMismatches())
            .thenReturn(List.of(
                new LikeCountMismatch(1L, 10L, 12L),
                new LikeCountMismatch(2L, 5L, 7L)));
        doThrow(new RuntimeException("DB error")).when(productRepository).updateLikeCount(1L, 12L);

        scheduler.reconcileLikeCounts();

        verify(productRepository).updateLikeCount(2L, 7L);
    }
}
