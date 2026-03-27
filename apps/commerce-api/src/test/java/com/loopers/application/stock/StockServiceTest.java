package com.loopers.application.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private StockRepository stockRepository;

    @DisplayName("재고 조회")
    @Nested
    class GetByProductId {

        @DisplayName("상품ID로 재고를 조회한다")
        @Test
        void returnsStockForProductId() {
            // arrange
            Long productId = 1L;
            StockModel stock = new StockModel(productId, 100);
            given(stockRepository.findByProductId(productId)).willReturn(Optional.of(stock));
            // act
            StockModel result = stockService.getByProductId(productId);
            // assert
            assertAll(
                () -> assertThat(result.getProductId()).isEqualTo(productId),
                () -> assertThat(result.getQuantity()).isEqualTo(100)
            );
        }

        @DisplayName("재고가 없으면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsOnNonExistentStock() {
            // arrange
            Long productId = 999L;
            given(stockRepository.findByProductId(productId)).willReturn(Optional.empty());
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                stockService.getByProductId(productId);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고 저장")
    @Nested
    class Save {

        @DisplayName("재고를 저장한다")
        @Test
        void savesStock() {
            // arrange
            StockModel stock = new StockModel(1L, 100);
            given(stockRepository.save(any(StockModel.class))).willReturn(stock);
            // act
            StockModel result = stockService.save(1L, 100);
            // assert
            assertThat(result.getQuantity()).isEqualTo(100);
            then(stockRepository).should().save(any(StockModel.class));
        }
    }

    @DisplayName("상품별 재고 배치 조회")
    @Nested
    class GetByProductIds {

        @DisplayName("productId 목록으로 재고 Map을 반환한다")
        @Test
        void returnsMapOfStocks() {
            // given
            List<Long> productIds = List.of(1L, 2L);
            StockModel stock1 = new StockModel(1L, 100);
            StockModel stock2 = new StockModel(2L, 50);
            when(stockRepository.findAllByProductIdIn(productIds))
                .thenReturn(List.of(stock1, stock2));

            // when
            Map<Long, StockModel> result = stockService.getByProductIds(productIds);

            // then
            assertAll(
                () -> assertThat(result).hasSize(2),
                () -> assertThat(result.get(1L).getQuantity()).isEqualTo(100),
                () -> assertThat(result.get(2L).getQuantity()).isEqualTo(50)
            );
        }
    }
}
