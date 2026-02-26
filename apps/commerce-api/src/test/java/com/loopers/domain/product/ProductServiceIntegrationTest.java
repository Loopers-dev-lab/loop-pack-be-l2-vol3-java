package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveBrand(String name) {
        BrandModel brand = brandService.register(name);
        return brand.getId();
    }

    @DisplayName("register 시")
    @Nested
    class Register {

        @DisplayName("유효한 브랜드 ID와 값이 주어지면 저장 후 상품을 반환한다.")
        @Test
        void register_withValidBrandAndInputs_shouldPersistAndReturn() {
            Long brandId = saveBrand("테스트 브랜드");
            ProductModel saved = productService.register(brandId, "테스트 상품", new BigDecimal("10000"), 10);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getBrandId()).isEqualTo(brandId);
            assertThat(saved.getName()).isEqualTo("테스트 상품");
            assertThat(saved.getPrice()).isEqualByComparingTo("10000");
            assertThat(saved.getStockQuantity()).isEqualTo(10);
            assertThat(saved.isDeleted()).isFalse();
        }

        @DisplayName("존재하지 않는 브랜드 ID면 NOT_FOUND 예외가 발생한다.")
        @Test
        void register_withNonExistentBrandId_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.register(999_999L, "상품", new BigDecimal("1000"), 1));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @Test
        void findById_withSavedProduct_shouldReturnPresent() {
            Long brandId = saveBrand("브랜드");
            ProductModel saved = productService.register(brandId, "상품", new BigDecimal("5000"), 5);

            Optional<ProductModel> result = productService.findById(saved.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("상품");
        }

        @Test
        void findById_withNonExistentId_shouldReturnEmpty() {
            assertThat(productService.findById(999_999L)).isEmpty();
        }
    }

    @DisplayName("findByIdAndNotDeleted 시")
    @Nested
    class FindByIdAndNotDeleted {

        @Test
        void findByIdAndNotDeleted_withNotDeletedProduct_shouldReturnPresent() {
            Long brandId = saveBrand("브랜드");
            ProductModel saved = productService.register(brandId, "상품", new BigDecimal("3000"), 3);

            Optional<ProductModel> result = productService.findByIdAndNotDeleted(saved.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("상품");
        }
    }

    @DisplayName("update 시")
    @Nested
    class Update {

        @Test
        void update_withNonExistentId_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.update(999_999L, "이름", new BigDecimal("1000"), 1));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void update_withValidInputs_shouldPersistUpdate() {
            Long brandId = saveBrand("브랜드");
            ProductModel saved = productService.register(brandId, "기존명", new BigDecimal("1000"), 5);

            ProductModel updated = productService.update(saved.getId(), "새이름", new BigDecimal("2000"), 10);

            assertThat(updated.getName()).isEqualTo("새이름");
            assertThat(updated.getPrice()).isEqualByComparingTo("2000");
            assertThat(updated.getStockQuantity()).isEqualTo(10);
            Optional<ProductModel> found = productService.findByIdAndNotDeleted(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("새이름");
        }
    }

    @DisplayName("validateProductAvailability 시")
    @Nested
    class ValidateProductAvailability {

        @Test
        void validateProductAvailability_whenProductNotFound_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateProductAvailability(999_999L, 1, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void validateProductAvailability_whenInsufficientStock_shouldThrowBadRequest() {
            Long brandId = saveBrand("브랜드");
            ProductModel saved = productService.register(brandId, "상품", new BigDecimal("1000"), 2);

            CoreException ex = assertThrows(CoreException.class, () ->
                productService.validateProductAvailability(saved.getId(), 10, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void validateProductAvailability_whenValid_shouldNotThrow() {
            Long brandId = saveBrand("브랜드");
            ProductModel saved = productService.register(brandId, "상품", new BigDecimal("1000"), 10);
            productService.validateProductAvailability(saved.getId(), 5, 100L);
        }
    }

    @DisplayName("validateProducts 시")
    @Nested
    class ValidateProducts {

        @Test
        void validateProducts_whenAllValid_shouldNotThrow() {
            Long brandId = saveBrand("브랜드");
            ProductModel p = productService.register(brandId, "상품", new BigDecimal("1000"), 10);
            productService.validateProducts(List.of(
                new ProductValidationRequest(p.getId(), 2, null),
                new ProductValidationRequest(p.getId(), 3, 1L)
            ));
        }
    }

    @DisplayName("restoreStock 시")
    @Nested
    class RestoreStock {

        @Test
        void restoreStock_whenProductNotFound_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () ->
                productService.restoreStock(List.of(new RestoreStockItem(999_999L, 1))));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void restoreStock_whenValid_shouldIncreaseStock() {
            Long brandId = saveBrand("브랜드");
            ProductModel saved = productService.register(brandId, "상품", new BigDecimal("1000"), 5);

            productService.restoreStock(List.of(new RestoreStockItem(saved.getId(), 3)));

            Optional<ProductModel> found = productService.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getStockQuantity()).isEqualTo(8);
        }

        @Test
        void restoreStock_concurrentCalls_shouldNotLoseQuantity() throws InterruptedException {
            Long brandId = saveBrand("브랜드");
            ProductModel saved = productService.register(brandId, "상품", new BigDecimal("1000"), 0);
            Long productId = saved.getId();
            int threadCount = 10;
            int quantityPerThread = 1;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger errors = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        productService.restoreStock(List.of(new RestoreStockItem(productId, quantityPerThread)));
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            done.await();
            executor.shutdown();

            assertThat(errors.get()).isZero();
            Optional<ProductModel> found = productService.findById(productId);
            assertThat(found).isPresent();
            assertThat(found.get().getStockQuantity()).isEqualTo(threadCount * quantityPerThread);
        }
    }
}
