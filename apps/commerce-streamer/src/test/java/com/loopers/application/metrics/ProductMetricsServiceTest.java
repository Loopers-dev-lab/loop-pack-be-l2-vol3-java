package com.loopers.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;

@ExtendWith(MockitoExtension.class)
class ProductMetricsServiceTest {

    @InjectMocks
    private ProductMetricsService productMetricsService;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @DisplayName("incrementLikeCount를 호출할 때,")
    @Nested
    class IncrementLikeCount {

        @DisplayName("기존 ProductMetrics가 없으면, 새로 생성하고 likeCount를 1 증가시킨다.")
        @Test
        void createsAndIncrements_whenNotExists() {
            // arrange
            given(productMetricsRepository.findByProductId(1L)).willReturn(Optional.empty());
            given(productMetricsRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            // act
            productMetricsService.incrementLikeCount(1L);

            // assert
            ArgumentCaptor<ProductMetrics> captor = ArgumentCaptor.forClass(ProductMetrics.class);
            verify(productMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("기존 ProductMetrics가 있으면, likeCount를 1 증가시킨다.")
        @Test
        void increments_whenExists() {
            // arrange
            ProductMetrics existing = ProductMetrics.create(1L);
            given(productMetricsRepository.findByProductId(1L)).willReturn(Optional.of(existing));

            // act
            productMetricsService.incrementLikeCount(1L);

            // assert
            assertThat(existing.getLikeCount()).isEqualTo(1L);
            verify(productMetricsRepository, never()).save(any());
        }
    }

    @DisplayName("decrementLikeCount를 호출할 때,")
    @Nested
    class DecrementLikeCount {

        @DisplayName("기존 ProductMetrics가 있으면, likeCount를 1 감소시킨다.")
        @Test
        void decrements_whenExists() {
            // arrange
            ProductMetrics existing = ProductMetrics.create(1L);
            existing.incrementLikeCount();
            existing.incrementLikeCount();
            given(productMetricsRepository.findByProductId(1L)).willReturn(Optional.of(existing));

            // act
            productMetricsService.decrementLikeCount(1L);

            // assert
            assertThat(existing.getLikeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("addOrderCount를 호출할 때,")
    @Nested
    class AddOrderCount {

        @DisplayName("기존 ProductMetrics가 없으면, 새로 생성하고 수량을 추가한다.")
        @Test
        void createsAndAdds_whenNotExists() {
            // arrange
            given(productMetricsRepository.findByProductId(1L)).willReturn(Optional.empty());
            given(productMetricsRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

            // act
            productMetricsService.addOrderCount(1L, 3L);

            // assert
            ArgumentCaptor<ProductMetrics> captor = ArgumentCaptor.forClass(ProductMetrics.class);
            verify(productMetricsRepository).save(captor.capture());
            assertThat(captor.getValue().getOrderCount()).isEqualTo(3L);
        }

        @DisplayName("기존 ProductMetrics가 있으면, 수량을 누적한다.")
        @Test
        void addsToExisting_whenExists() {
            // arrange
            ProductMetrics existing = ProductMetrics.create(1L);
            existing.addOrderCount(2L);
            given(productMetricsRepository.findByProductId(1L)).willReturn(Optional.of(existing));

            // act
            productMetricsService.addOrderCount(1L, 5L);

            // assert
            assertThat(existing.getOrderCount()).isEqualTo(7L);
            verify(productMetricsRepository, never()).save(any());
        }
    }
}
