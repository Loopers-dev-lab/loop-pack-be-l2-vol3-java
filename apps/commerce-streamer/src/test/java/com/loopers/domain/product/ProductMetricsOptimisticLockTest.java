package com.loopers.domain.product;

import com.loopers.infrastructure.product.ProductMetricsJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.kafka.listener.auto-startup=false"
})
class ProductMetricsOptimisticLockTest {

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @AfterEach
    void tearDown() {
        productMetricsJpaRepository.deleteAll();
    }

    @DisplayName("같은 row를 동시에 수정하면 두 번째 저장 시 ObjectOptimisticLockingFailureException이 발생한다.")
    @Test
    void concurrentUpdate_throwsOptimisticLockException() {
        // arrange: 초기 ProductMetrics 저장 (version=0)
        LocalDate today = LocalDate.now();
        productMetricsJpaRepository.save(new ProductMetrics(9999L, today, 0));

        // 두 스레드가 각각 같은 row를 읽는다 (version=0)
        ProductMetrics metrics1 = productMetricsJpaRepository.findByProductIdAndDate(9999L, today).orElseThrow();
        ProductMetrics metrics2 = productMetricsJpaRepository.findByProductIdAndDate(9999L, today).orElseThrow();

        // 첫 번째 스레드가 먼저 저장 (version: 0 → 1)
        metrics1.increaseLikeCount();
        productMetricsJpaRepository.saveAndFlush(metrics1);

        // 두 번째 스레드가 저장 시도 → version=0 이지만 DB는 version=1 → 충돌
        metrics2.increaseLikeCount();
        assertThatThrownBy(() -> productMetricsJpaRepository.saveAndFlush(metrics2))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // DB에는 첫 번째 저장 결과만 반영됨
        ProductMetrics result = productMetricsJpaRepository.findByProductIdAndDate(9999L, today).orElseThrow();
        assertThat(result.getLikeCount()).isEqualTo(1);
    }
}
