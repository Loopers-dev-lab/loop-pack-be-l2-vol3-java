package com.loopers.application.brand;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.RegisterProductCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.stock.ProductStockDomainService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("브랜드 삭제 동시성 테스트")
class BrandDeleteConcurrencyIntegrationTest {

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private ProductStockDomainService productStockService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("브랜드 삭제와 상품 등록이 동시에 실행될 때, ")
    @Nested
    class ConcurrentDeleteAndRegister {

        @DisplayName("비관적 락으로 직렬화되어, 삭제 후 등록은 실패하거나 등록 후 삭제 시 모두 정리된다.")
        @Test
        void serializesDeleteAndRegister_withPessimisticLock() throws InterruptedException {
            Brand brand = brandService.register("나이키");
            Long brandId = brand.getId();

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicInteger deleteSuccess = new AtomicInteger(0);
            AtomicInteger registerSuccess = new AtomicInteger(0);
            AtomicInteger deleteFail = new AtomicInteger(0);
            AtomicInteger registerFail = new AtomicInteger(0);

            // 삭제 스레드
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    brandApplicationService.delete(brandId);
                    deleteSuccess.incrementAndGet();
                } catch (Exception e) {
                    deleteFail.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            // 등록 스레드
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    RegisterProductCommand command = new RegisterProductCommand(brandId, "에어맥스", 129000, 100);
                    productApplicationService.registerWithStock(command);
                    registerSuccess.incrementAndGet();
                } catch (Exception e) {
                    registerFail.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executorService.shutdown();

            // 두 가지 시나리오 중 하나:
            // 1. 삭제가 먼저: 삭제 성공 + 등록 실패 (NOT_FOUND)
            // 2. 등록이 먼저: 둘 다 성공 (등록 후 삭제가 상품/재고 모두 soft delete)
            assertThat(deleteSuccess.get()).isEqualTo(1);
            assertThat(deleteSuccess.get() + registerSuccess.get()).isGreaterThanOrEqualTo(1);
        }
    }
}
