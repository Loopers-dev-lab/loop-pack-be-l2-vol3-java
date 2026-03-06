package com.loopers.domain.order;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.service.ProductService;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 데드락 발생 조건과 방지 전략을 검증하는 테스트.
 *
 * 핵심: 한 트랜잭션 안에서 여러 row에 락(비관적 락/UPDATE)을 잡을 때,
 * 스레드마다 다른 순서로 락을 잡으면 데드락이 발생한다.
 * ID 오름차순 정렬로 락 순서를 통일하면 데드락이 방지된다.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("데드락 테스트")
class DeadlockTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productAId;
    private Long productBId;

    @BeforeEach
    void setUp() {
        productJpaRepository.deleteAll();

        productAId = transactionTemplate.execute(status -> {
            Product p = productService.createProduct(1L, new ProductCommand.Create(1L, "상품A", 10000, 100));
            return p.getId();
        });
        productBId = transactionTemplate.execute(status -> {
            Product p = productService.createProduct(1L, new ProductCommand.Create(1L, "상품B", 20000, 100));
            return p.getId();
        });
    }

    @Nested
    @DisplayName("비관적 락 - 정렬 없이 역순으로 락 획득")
    class PessimisticLockWithoutSorting {

        @Test
        @DisplayName("스레드 A는 상품A→B 순, 스레드 B는 상품B→A 순으로 락을 잡으면 데드락이 발생한다")
        void deadlockOccurs_whenLockOrderReversed() throws InterruptedException {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            AtomicReference<String> failReason = new AtomicReference<>("");

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            // 스레드 A: 상품A 락 → 상품B 락 (오름차순)
            executorService.execute(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        readyLatch.countDown();
                        try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                        ProductEntity a = productJpaRepository.findByIdWithPessimisticLock(productAId).orElseThrow();
                        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        ProductEntity b = productJpaRepository.findByIdWithPessimisticLock(productBId).orElseThrow();

                        a.update(a.getName(), a.getPrice(), a.getStock() - 1, a.getDisplayStatus());
                        b.update(b.getName(), b.getPrice(), b.getStock() - 1, b.getDisplayStatus());
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failReason.set(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });

            // 스레드 B: 상품B 락 → 상품A 락 (역순 — 데드락 유발)
            executorService.execute(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        readyLatch.countDown();
                        try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                        ProductEntity b = productJpaRepository.findByIdWithPessimisticLock(productBId).orElseThrow();
                        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        ProductEntity a = productJpaRepository.findByIdWithPessimisticLock(productAId).orElseThrow();

                        a.update(a.getName(), a.getPrice(), a.getStock() - 1, a.getDisplayStatus());
                        b.update(b.getName(), b.getPrice(), b.getStock() - 1, b.getDisplayStatus());
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failReason.set(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });

            readyLatch.await();
            startLatch.countDown();
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);

            // 데드락 발생 시 MySQL이 하나를 롤백 → 1성공 1실패
            assertThat(failCount.get()).isEqualTo(1);
            assertThat(successCount.get()).isEqualTo(1);
            System.out.println("[비관적 락 - 정렬 없음] 데드락 발생! 실패 원인: " + failReason.get());
        }
    }

    @Nested
    @DisplayName("비관적 락 - ID 오름차순 정렬 후 락 획득")
    class PessimisticLockWithSorting {

        @Test
        @DisplayName("두 스레드 모두 상품A→B 순으로 락을 잡으면 데드락이 발생하지 않는다")
        void noDeadlock_whenLockOrderConsistent() throws InterruptedException {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            for (int i = 0; i < 2; i++) {
                executorService.execute(() -> {
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            readyLatch.countDown();
                            try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                            // ID 오름차순 정렬 후 락 획득 — 데드락 방지
                            Long[] ids = {productAId, productBId};
                            Arrays.sort(ids);

                            ProductEntity first = productJpaRepository.findByIdWithPessimisticLock(ids[0]).orElseThrow();
                            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            ProductEntity second = productJpaRepository.findByIdWithPessimisticLock(ids[1]).orElseThrow();

                            first.update(first.getName(), first.getPrice(), first.getStock() - 1, first.getDisplayStatus());
                            second.update(second.getName(), second.getPrice(), second.getStock() - 1, second.getDisplayStatus());
                        });
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);

            // 데드락 없이 둘 다 성공
            assertThat(successCount.get()).isEqualTo(2);
            assertThat(failCount.get()).isEqualTo(0);

            // 재고 확인: 각각 2씩 차감
            Product resultA = productService.findProduct(productAId);
            Product resultB = productService.findProduct(productBId);
            assertThat(resultA.getStock().value()).isEqualTo(98);
            assertThat(resultB.getStock().value()).isEqualTo(98);
        }
    }

    @Nested
    @DisplayName("Atomic UPDATE - 정렬 유무에 따른 데드락")
    class AtomicUpdate {

        @Test
        @DisplayName("Atomic UPDATE도 역순 접근 시 데드락이 발생한다 (UPDATE도 트랜잭션 내에서 row lock 보유)")
        void deadlockOccurs_withAtomicUpdate_whenOrderReversed() throws InterruptedException {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            AtomicReference<String> failReason = new AtomicReference<>("");

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            // 스레드 A: 상품A → 상품B (오름차순)
            executorService.execute(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        readyLatch.countDown();
                        try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                        productJpaRepository.decreaseStock(productAId, 1);
                        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        productJpaRepository.decreaseStock(productBId, 1);
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failReason.set(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });

            // 스레드 B: 상품B → 상품A (역순)
            executorService.execute(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        readyLatch.countDown();
                        try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                        productJpaRepository.decreaseStock(productBId, 1);
                        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        productJpaRepository.decreaseStock(productAId, 1);
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    failReason.set(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });

            readyLatch.await();
            startLatch.countDown();
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);

            // Atomic UPDATE도 데드락 발생 — UPDATE가 트랜잭션 내에서 row lock을 잡기 때문
            assertThat(failCount.get()).isEqualTo(1);
            assertThat(successCount.get()).isEqualTo(1);
            System.out.println("[Atomic UPDATE - 정렬 없음] 데드락 발생! 실패 원인: " + failReason.get());
        }

        @Test
        @DisplayName("Atomic UPDATE도 ID 정렬하면 데드락이 발생하지 않는다")
        void noDeadlock_withAtomicUpdate_whenSorted() throws InterruptedException {
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            for (int i = 0; i < 2; i++) {
                executorService.execute(() -> {
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            readyLatch.countDown();
                            try { startLatch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                            Long[] ids = {productAId, productBId};
                            Arrays.sort(ids);

                            productJpaRepository.decreaseStock(ids[0], 1);
                            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            productJpaRepository.decreaseStock(ids[1], 1);
                        });
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            executorService.shutdown();
            executorService.awaitTermination(30, TimeUnit.SECONDS);

            // 정렬하면 데드락 없이 둘 다 성공
            assertThat(successCount.get()).isEqualTo(2);
            assertThat(failCount.get()).isEqualTo(0);

            Product resultA = productService.findProduct(productAId);
            Product resultB = productService.findProduct(productBId);
            assertThat(resultA.getStock().value()).isEqualTo(98);
            assertThat(resultB.getStock().value()).isEqualTo(98);
        }
    }
}
