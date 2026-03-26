package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductMetricsService 단위 테스트")
class ProductMetricsServiceTest {

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @InjectMocks
    private ProductMetricsService productMetricsService;

    @Nested
    @DisplayName("findOrCreateByProductId - 조회 또는 생성")
    class FindOrCreateByProductId {

        @Test
        @DisplayName("성공: 존재하면 기존 ProductMetrics를 반환한다")
        void findOrCreate_existingMetrics() {
            // Given
            Long productId = 100L;
            ProductMetrics existing = ProductMetrics.create(productId);
            given(productMetricsRepository.findByProductId(productId)).willReturn(Optional.of(existing));

            // When
            ProductMetrics result = productMetricsService.findOrCreateByProductId(productId);

            // Then
            assertThat(result).isEqualTo(existing);
        }

        @Test
        @DisplayName("성공: 존재하지 않으면 새로 생성하여 저장한다")
        void findOrCreate_createsNew() {
            // Given
            Long productId = 100L;
            given(productMetricsRepository.findByProductId(productId)).willReturn(Optional.empty());
            given(productMetricsRepository.save(any(ProductMetrics.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            ProductMetrics result = productMetricsService.findOrCreateByProductId(productId);

            // Then
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getLikesCount()).isZero();
            then(productMetricsRepository).should().save(any(ProductMetrics.class));
        }
    }

    @Nested
    @DisplayName("increaseLikes - 좋아요 증가")
    class IncreaseLikes {

        @Test
        @DisplayName("성공: ProductMetrics의 likesCount를 증가시킨다")
        void increaseLikes_incrementsCount() {
            // Given
            Long productId = 100L;
            ProductMetrics metrics = ProductMetrics.create(productId);
            given(productMetricsRepository.findByProductId(productId)).willReturn(Optional.of(metrics));

            // When
            productMetricsService.increaseLikes(productId);

            // Then
            assertThat(metrics.getLikesCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("decreaseLikes - 좋아요 감소")
    class DecreaseLikes {

        @Test
        @DisplayName("성공: ProductMetrics의 likesCount를 감소시킨다")
        void decreaseLikes_decrementsCount() {
            // Given
            Long productId = 100L;
            ProductMetrics metrics = ProductMetrics.create(productId);
            metrics.increaseLikes();
            metrics.increaseLikes();
            given(productMetricsRepository.findByProductId(productId)).willReturn(Optional.of(metrics));

            // When
            productMetricsService.decreaseLikes(productId);

            // Then
            assertThat(metrics.getLikesCount()).isEqualTo(1L);
        }
    }
}
