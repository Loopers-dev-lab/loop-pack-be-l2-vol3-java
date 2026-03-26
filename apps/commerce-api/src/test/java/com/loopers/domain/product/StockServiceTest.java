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

import java.util.Optional;

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
            when(productStockRepository.reserveStock(1L, 5)).thenReturn(1);

            assertThatCode(() -> stockService.hold(1L, 5))
                    .doesNotThrowAnyException();
            verify(productStockRepository).reserveStock(1L, 5);
        }

        @Test
        @DisplayName("재고 부족 시 STOCK_NOT_ENOUGH 예외가 발생한다")
        void hold_WithInsufficientStock_ShouldThrowSTOCK_NOT_ENOUGH() {
            when(productStockRepository.reserveStock(1L, 100)).thenReturn(0);

            assertThatThrownBy(() -> stockService.hold(1L, 100))
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
            when(productStockRepository.releaseStock(1L, 5)).thenReturn(1);

            assertThatCode(() -> stockService.release(1L, 5))
                    .doesNotThrowAnyException();
            verify(productStockRepository).releaseStock(1L, 5);
        }

        @Test
        @DisplayName("예약량보다 많은 해제 시도 시 예외가 발생한다")
        void release_WithExcessiveQty_ShouldThrow() {
            when(productStockRepository.releaseStock(1L, 100)).thenReturn(0);

            assertThatThrownBy(() -> stockService.release(1L, 100))
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
            when(productStockRepository.commitStock(1L, 5)).thenReturn(1);

            assertThatCode(() -> stockService.commit(1L, 5))
                    .doesNotThrowAnyException();
            verify(productStockRepository).commitStock(1L, 5);
        }
    }

    @Nested
    @DisplayName("가용 재고 확인 (ValidateAvailability)")
    class ValidateAvailabilityTests {

        @Test
        @DisplayName("가용 재고가 충분하면 예외 없이 정상 완료된다")
        void validateAvailability_WithSufficientStock_ShouldNotThrow() {
            ProductStockModel stock = ProductStockModel.create(1L, 10);
            when(productStockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

            assertThatCode(() -> stockService.validateAvailability(1L, 5))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("가용 재고가 부족하면 STOCK_NOT_ENOUGH 예외가 발생한다")
        void validateAvailability_WithInsufficientStock_ShouldThrow() {
            ProductStockModel stock = ProductStockModel.createWithReserved(1L, 10, 8);
            when(productStockRepository.findByProductId(1L)).thenReturn(Optional.of(stock));

            assertThatThrownBy(() -> stockService.validateAvailability(1L, 5))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.STOCK_NOT_ENOUGH));
        }

        @Test
        @DisplayName("상품 재고가 존재하지 않으면 PRODUCT_NOT_FOUND 예외가 발생한다")
        void validateAvailability_WithNoStock_ShouldThrowProductNotFound() {
            when(productStockRepository.findByProductId(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockService.validateAvailability(999L, 1))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }
}
