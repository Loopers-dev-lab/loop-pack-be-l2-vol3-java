package com.loopers.infrastructure.inventory;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InventoryRepositoryIntegrationTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product createProduct(String name) {
        Brand brand = brandRepository.save(Brand.register("브랜드", "설명"));
        return productRepository.save(Product.register(brand.getId(), name, "설명", 10000));
    }

    @Nested
    @DisplayName("findByProductId 메서드는")
    class FindByProductId {

        @Test
        void 존재하는_상품의_재고를_반환한다() {
            // arrange
            Product product = createProduct("상품");
            inventoryRepository.save(Inventory.initialize(product.getId(), 100));

            // act
            Optional<Inventory> result = inventoryRepository.findByProductId(product.getId());

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getQuantity()).isEqualTo(100);
        }

        @Test
        void 존재하지_않는_상품이면_empty를_반환한다() {
            // act
            Optional<Inventory> result = inventoryRepository.findByProductId(999L);

            // assert
            assertThat(result).isEmpty();
        }

        @Test
        void 소프트_삭제된_재고는_조회되지_않는다() {
            // arrange
            Product product = createProduct("상품");
            Inventory inventory = Inventory.initialize(product.getId(), 100);
            inventory.discard();
            inventoryRepository.save(inventory);

            // act
            Optional<Inventory> result = inventoryRepository.findByProductId(product.getId());

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByProductIdForUpdate 메서드는")
    class FindByProductIdForUpdate {

        @Test
        @Transactional
        void 비관적_락으로_재고를_조회한다() {
            // arrange
            Product product = createProduct("상품");
            inventoryRepository.save(Inventory.initialize(product.getId(), 100));

            // act
            Optional<Inventory> result = inventoryRepository.findByProductIdForUpdate(product.getId());

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getQuantity()).isEqualTo(100);
        }

        @Test
        @Transactional
        void 소프트_삭제된_재고는_조회되지_않는다() {
            // arrange
            Product product = createProduct("상품");
            Inventory inventory = Inventory.initialize(product.getId(), 100);
            inventory.discard();
            inventoryRepository.save(inventory);

            // act
            Optional<Inventory> result = inventoryRepository.findByProductIdForUpdate(product.getId());

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByProductIdIn 메서드는")
    class FindAllByProductIdIn {

        @Test
        void 여러_상품의_재고를_일괄_조회한다() {
            // arrange
            Product product1 = createProduct("상품1");
            Product product2 = createProduct("상품2");
            inventoryRepository.save(Inventory.initialize(product1.getId(), 100));
            inventoryRepository.save(Inventory.initialize(product2.getId(), 50));

            // act
            List<Inventory> result = inventoryRepository.findAllByProductIdIn(
                    List.of(product1.getId(), product2.getId()));

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void 소프트_삭제된_재고는_제외된다() {
            // arrange
            Product product1 = createProduct("상품1");
            Product product2 = createProduct("상품2");
            inventoryRepository.save(Inventory.initialize(product1.getId(), 100));

            Inventory deleted = Inventory.initialize(product2.getId(), 50);
            deleted.discard();
            inventoryRepository.save(deleted);

            // act
            List<Inventory> result = inventoryRepository.findAllByProductIdIn(
                    List.of(product1.getId(), product2.getId()));

            // assert
            assertThat(result).hasSize(1);
        }
    }
}
