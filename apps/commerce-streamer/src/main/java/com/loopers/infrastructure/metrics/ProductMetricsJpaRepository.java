package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    // PESSIMISTIC_WRITE: productId key ordering으로 같은 파티션 내 순차 처리가 보장되지만,
    // 멀티 인스턴스 streamer 배포 시 파티션 리밸런싱 과도기에 동일 row 동시 접근이 가능하므로 방어적 락 적용.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM ProductMetricsModel m WHERE m.refProductId = :refProductId")
    Optional<ProductMetricsModel> findByRefProductId(@Param("refProductId") Long refProductId);
}
