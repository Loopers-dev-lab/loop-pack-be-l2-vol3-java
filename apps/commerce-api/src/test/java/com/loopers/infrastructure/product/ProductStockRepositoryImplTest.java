package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ProductStockRepositoryImpl.class)
@ActiveProfiles("test")
@DisplayName("ProductStockRepository CAS 통합 테스트")
class ProductStockRepositoryImplTest {

    @Autowired
    ProductStockRepositoryImpl stockRepository;

    @Autowired
    TestEntityManager entityManager;

    private ProductStockModel createStock(Long productId, int onHand, int reserved) {
        ProductStockModel stock = ProductStockModel.createWithReserved(productId, onHand, reserved);
        return stockRepository.save(stock);
    }

    @Test
    @DisplayName("reserveStock CAS - 가용 재고 충분 시 affected=1 반환")
    void reserveStock_CAS_WithSufficientStock_ShouldReturnAffectedRows1() {
        createStock(1L, 100, 0);
        entityManager.flush();
        entityManager.clear();

        int affected = stockRepository.reserveStock(1L, 50);

        assertThat(affected).isEqualTo(1);
        ProductStockModel stock = stockRepository.findByProductId(1L).get();
        assertThat(stock.getReserved()).isEqualTo(50);
    }

    @Test
    @DisplayName("reserveStock CAS - 가용 재고 부족 시 affected=0 반환 (오버셀 방지)")
    void reserveStock_CAS_WithInsufficientStock_ShouldReturnAffectedRows0() {
        createStock(1L, 10, 5);
        entityManager.flush();
        entityManager.clear();

        int affected = stockRepository.reserveStock(1L, 10);

        assertThat(affected).isEqualTo(0);
        ProductStockModel stock = stockRepository.findByProductId(1L).get();
        assertThat(stock.getReserved()).isEqualTo(5);
    }

    @Test
    @DisplayName("releaseStock CAS - 예약 해제 성공")
    void releaseStock_CAS_ShouldDecreaseReserved() {
        createStock(1L, 100, 50);
        entityManager.flush();
        entityManager.clear();

        int affected = stockRepository.releaseStock(1L, 30);

        assertThat(affected).isEqualTo(1);
        ProductStockModel stock = stockRepository.findByProductId(1L).get();
        assertThat(stock.getReserved()).isEqualTo(20);
    }

    @Test
    @DisplayName("commitStock CAS - onHand와 reserved 동시 차감 성공")
    void commitStock_CAS_ShouldDecreaseBothOnHandAndReserved() {
        createStock(1L, 100, 50);
        entityManager.flush();
        entityManager.clear();

        int affected = stockRepository.commitStock(1L, 30);

        assertThat(affected).isEqualTo(1);
        ProductStockModel stock = stockRepository.findByProductId(1L).get();
        assertThat(stock.getOnHand()).isEqualTo(70);
        assertThat(stock.getReserved()).isEqualTo(20);
    }
}
