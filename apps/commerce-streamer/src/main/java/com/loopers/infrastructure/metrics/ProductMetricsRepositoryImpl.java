package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {
    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @PersistenceContext
    private EntityManager em;

    @Override
    public ProductMetrics save(ProductMetrics productMetrics) {
        return productMetricsJpaRepository.save(productMetrics);
    }

    @Override
    public Optional<ProductMetrics> findById(Long productId) {
        return productMetricsJpaRepository.findById(productId);
    }

    @Override
    public void insertIgnore(Long productId) {
        productMetricsJpaRepository.insertIgnore(productId);
    }

    @Override
    public int incrementLikeCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.incrementLikeCount(productId, occurredAt);
    }

    @Override
    public int decrementLikeCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.decrementLikeCount(productId, occurredAt);
    }

    @Override
    public int incrementViewCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.incrementViewCount(productId, occurredAt);
    }

    @Override
    public int incrementSalesCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.incrementSalesCount(productId, occurredAt);
    }

    @Override
    public int decrementSalesCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.decrementSalesCount(productId, occurredAt);
    }

    @Override
    public void bulkInsertIgnoreProducts(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return;

        StringBuilder sb = new StringBuilder(
                "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, updated_at) VALUES ");
        int i = 0;
        for (Long ignored : productIds) {
            if (i > 0) sb.append(", ");
            sb.append("(:pid").append(i).append(", 0, 0, 0, '2000-01-01 00:00:00')");
            i++;
        }
        sb.append(" ON DUPLICATE KEY UPDATE product_id = product_id");

        Query q = em.createNativeQuery(sb.toString());
        int j = 0;
        for (Long pid : productIds) {
            q.setParameter("pid" + j, pid);
            j++;
        }
        q.executeUpdate();
    }

    @Override
    public void bulkUpsertAllTime(List<AllTimeDelta> deltas) {
        if (deltas == null || deltas.isEmpty()) return;

        // bulkInsertIgnoreProducts 가 먼저 호출되어 행이 보장되므로 insert 브랜치는 실행되지 않는다.
        // 따라서 raw delta 를 그대로 전달해서 ON DUPLICATE KEY UPDATE 쪽에서 정확히 가감한다.
        StringBuilder sb = new StringBuilder(
                "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, updated_at) VALUES ");
        for (int i = 0; i < deltas.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("(:pid").append(i)
                    .append(", :ld").append(i)
                    .append(", :vd").append(i)
                    .append(", :sd").append(i)
                    .append(", :ts").append(i).append(")");
        }
        // out-of-order 보호: 기존 updated_at <= 배치 occurredAt 일 때만 갱신
        sb.append(" ON DUPLICATE KEY UPDATE ")
                .append("like_count = IF(updated_at <= VALUES(updated_at), GREATEST(like_count + VALUES(like_count), 0), like_count), ")
                .append("view_count = IF(updated_at <= VALUES(updated_at), view_count + VALUES(view_count), view_count), ")
                .append("sales_count = IF(updated_at <= VALUES(updated_at), GREATEST(sales_count + VALUES(sales_count), 0), sales_count), ")
                .append("updated_at = IF(updated_at <= VALUES(updated_at), VALUES(updated_at), updated_at)");

        Query q = em.createNativeQuery(sb.toString());
        for (int i = 0; i < deltas.size(); i++) {
            AllTimeDelta d = deltas.get(i);
            q.setParameter("pid" + i, d.productId());
            q.setParameter("ld" + i, d.likeDelta());
            q.setParameter("vd" + i, d.viewDelta());
            q.setParameter("sd" + i, d.salesDelta());
            q.setParameter("ts" + i, Timestamp.from(d.occurredAt().toInstant()));
        }
        q.executeUpdate();
    }
}
