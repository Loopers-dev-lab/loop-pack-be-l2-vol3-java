package com.loopers.application.product;

import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductStockApplicationServiceConcurrencyTest {

    private final ProductStockApplicationService productStockApplicationService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductStockApplicationServiceConcurrencyTest(
            ProductStockApplicationService productStockApplicationService,
            ProductRepository productRepository,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.productStockApplicationService = productStockApplicationService;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("재고 10개 상품에 20건 동시 차감 요청 시 10건만 성공하고 재고는 0이 된다")
    void reserveForOrder_concurrentRequests_onlyStockCountSucceeds() throws InterruptedException {
        Long brandId = brandRepository.save(new Brand(new BrandName("STOCK_CONC_BRAND"), "", "")).id();
        Long categoryId = categoryRepository.save(new Category("STOCK_CONC_CATEGORY")).id();
        Long productId = productRepository.save(new Product("동시성 재고 상품", 10_000, 10, "desc", categoryId, brandId)).id();

        int threadCount = 20;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    productStockApplicationService.reserveForOrder(List.of(new CreateOrderCommand.OrderItemCommand(productId, 1)));
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.BAD_REQUEST) {
                        outOfStockCount.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executorService.shutdownNow();

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(failures.get()).isZero();
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(outOfStockCount.get()).isEqualTo(10);
        assertThat(updated.stock()).isZero();
    }
}
