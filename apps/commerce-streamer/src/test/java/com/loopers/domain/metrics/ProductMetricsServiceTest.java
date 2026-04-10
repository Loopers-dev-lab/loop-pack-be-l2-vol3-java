package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductMetricsService 단위 테스트")
class ProductMetricsServiceTest {

    @Mock
    ProductMetricsRepository productMetricsRepository;

    @InjectMocks
    ProductMetricsService productMetricsService;

    @Test
    @DisplayName("incrementViewCount 호출 시 repository에 위임한다")
    void incrementViewCount_ShouldDelegateToRepository() {
        productMetricsService.incrementViewCount(1L);

        verify(productMetricsRepository).incrementViewCount(1L);
    }

    @Test
    @DisplayName("incrementLikeCount 호출 시 repository에 위임한다")
    void incrementLikeCount_ShouldDelegateToRepository() {
        productMetricsService.incrementLikeCount(2L);

        verify(productMetricsRepository).incrementLikeCount(2L);
    }

    @Test
    @DisplayName("decrementLikeCount 호출 시 repository에 위임한다")
    void decrementLikeCount_ShouldDelegateToRepository() {
        productMetricsService.decrementLikeCount(3L);

        verify(productMetricsRepository).decrementLikeCount(3L);
    }

    @Test
    @DisplayName("incrementOrderCount 호출 시 repository에 위임한다")
    void incrementOrderCount_ShouldDelegateToRepository() {
        productMetricsService.incrementOrderCount(4L, 50000L);

        verify(productMetricsRepository).incrementOrderCount(4L, 50000L);
    }

    @Test
    @DisplayName("findAll 호출 시 repository에 위임한다")
    void findAll_ShouldDelegateToRepository() {
        productMetricsService.findAll();

        verify(productMetricsRepository).findAll();
    }
}
