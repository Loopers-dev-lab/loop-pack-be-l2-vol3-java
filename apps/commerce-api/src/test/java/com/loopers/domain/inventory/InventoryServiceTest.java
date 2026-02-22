package com.loopers.domain.inventory;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.InventoryErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = Mockito.mock(InventoryRepository.class);
        inventoryService = new InventoryService(inventoryRepository);
    }

    @DisplayName("재고를 생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_생성된_재고가_반환된다() {
            // arrange
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            Inventory inventory = inventoryService.create(1L, 100);

            // assert
            assertThat(inventory)
                    .extracting(Inventory::getProductId, Inventory::getQuantity, Inventory::getReservedQty)
                    .containsExactly(1L, 100, 0);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            inventoryService.create(1L, 100);

            // assert
            verify(inventoryRepository).save(any(Inventory.class));
        }
    }

    @DisplayName("상품별 재고를 조회할 때,")
    @Nested
    class 상품별조회 {

        @Test
        void 존재하지_않는_상품이면_예외가_발생한다() {
            // arrange
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> inventoryService.getByProductId(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(InventoryErrorType.INVENTORY_NOT_FOUND);
        }

        @Test
        void 존재하는_상품이면_재고를_반환한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

            // act
            Inventory result = inventoryService.getByProductId(1L);

            // assert
            assertThat(result.getQuantity()).isEqualTo(100);
        }
    }

    @DisplayName("일괄 예약할 때,")
    @Nested
    class 일괄예약 {

        @Test
        void 존재하지_않는_상품이면_예외가_발생한다() {
            // arrange
            when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> inventoryService.reserveAll(Map.of(1L, 5)))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(InventoryErrorType.INVENTORY_NOT_FOUND);
        }

        @Test
        void 하나라도_재고가_부족하면_예외가_발생한다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 3);
            when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

            // act & assert
            assertThatThrownBy(() -> inventoryService.reserveAll(Map.of(1L, 5)))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(InventoryErrorType.INSUFFICIENT_STOCK);
        }

        @Test
        void 모든_상품의_재고가_충분하면_예약에_성공한다() {
            // arrange
            Inventory inventory1 = Inventory.create(1L, 100);
            Inventory inventory2 = Inventory.create(2L, 50);
            when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory1));
            when(inventoryRepository.findByProductIdForUpdate(2L)).thenReturn(Optional.of(inventory2));

            // act
            inventoryService.reserveAll(Map.of(1L, 10, 2L, 5));

            // assert
            assertThat(inventory1.getReservedQty()).isEqualTo(10);
            assertThat(inventory2.getReservedQty()).isEqualTo(5);
        }
    }

    @DisplayName("일괄 확정할 때,")
    @Nested
    class 일괄확정 {

        @Test
        void 각_재고의_commit이_호출된다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);
            inventory.reserve(10);
            when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

            // act
            inventoryService.commitAll(Map.of(1L, 10));

            // assert
            assertThat(inventory)
                    .extracting(Inventory::getQuantity, Inventory::getReservedQty)
                    .containsExactly(90, 0);
        }
    }

    @DisplayName("일괄 해제할 때,")
    @Nested
    class 일괄해제 {

        @Test
        void 각_재고의_release가_호출된다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);
            inventory.reserve(10);
            when(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(Optional.of(inventory));

            // act
            inventoryService.releaseAll(Map.of(1L, 10));

            // assert
            assertThat(inventory)
                    .extracting(Inventory::getQuantity, Inventory::getReservedQty)
                    .containsExactly(100, 0);
        }
    }

    @DisplayName("재고를 삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 존재하는_재고면_소프트_삭제된다() {
            // arrange
            Inventory inventory = Inventory.create(1L, 100);
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

            // act
            inventoryService.delete(1L);

            // assert
            assertThat(inventory.getDeletedAt()).isNotNull();
        }

        @Test
        void 존재하지_않는_재고면_무시된다() {
            // arrange
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.empty());

            // act - 예외 없이 정상 종료
            inventoryService.delete(1L);

            // assert
            verify(inventoryRepository).findByProductId(1L);
        }
    }
}
