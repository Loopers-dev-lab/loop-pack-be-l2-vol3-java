package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockService 도메인 서비스 테스트")
class StockServiceTest {

    @Mock
    ProductStockRepository productStockRepository;

    @InjectMocks
    StockService stockService;

    @Nested
    @DisplayName("재고 예약 (Hold)")
    class HoldTests {

        @Test
        @DisplayName("충분한 재고가 있으면 예외 없이 정상 완료된다")
        void hold_WithSufficientStock_ShouldReturnTrue() {
            when(productStockRepository.reserveStock("product-1", 5)).thenReturn(1);

            assertThatCode(() -> stockService.hold("product-1", 5))
                    .doesNotThrowAnyException();
            verify(productStockRepository).reserveStock("product-1", 5);
        }

        @Test
        @DisplayName("재고 부족 시 STOCK_NOT_ENOUGH 예외가 발생한다")
        void hold_WithInsufficientStock_ShouldThrowSTOCK_NOT_ENOUGH() {
            when(productStockRepository.reserveStock("product-1", 100)).thenReturn(0);

            assertThatThrownBy(() -> stockService.hold("product-1", 100))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.STOCK_NOT_ENOUGH));
        }
    }

    @Nested
    @DisplayName("재고 해제 (Release)")
    class ReleaseTests {

        @Test
        @DisplayName("정상 해제 시 예외 없이 완료된다")
        void release_WithValidQty_ShouldReturnTrue() {
            when(productStockRepository.releaseStock("product-1", 5)).thenReturn(1);

            assertThatCode(() -> stockService.release("product-1", 5))
                    .doesNotThrowAnyException();
            verify(productStockRepository).releaseStock("product-1", 5);
        }

        @Test
        @DisplayName("예약량보다 많은 해제 시도 시 예외가 발생한다")
        void release_WithExcessiveQty_ShouldThrow() {
            when(productStockRepository.releaseStock("product-1", 100)).thenReturn(0);

            assertThatThrownBy(() -> stockService.release("product-1", 100))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.STOCK_NOT_ENOUGH));
        }
    }

    @Nested
    @DisplayName("재고 확정 (Commit)")
    class CommitTests {

        @Test
        @DisplayName("정상 확정 시 예외 없이 완료된다")
        void commit_WithValidQty_ShouldReturnTrue() {
            when(productStockRepository.commitStock("product-1", 5)).thenReturn(1);

            assertThatCode(() -> stockService.commit("product-1", 5))
                    .doesNotThrowAnyException();
            verify(productStockRepository).commitStock("product-1", 5);
        }
    }
}
