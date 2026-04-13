package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductDailyMetrics;
import com.loopers.domain.metrics.ProductDailyMetricsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class ProductDailyMetricsRepositoryImpl implements ProductDailyMetricsRepository {
    private final ProductDailyMetricsJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void upsertViewCount(Long productId, LocalDate date, ZonedDateTime updatedAt) {
        jpaRepository.upsertViewCount(productId, date, updatedAt);
    }

    @Override
    @Transactional
    public void upsertLikeCount(Long productId, LocalDate date, int delta, ZonedDateTime updatedAt) {
        jpaRepository.upsertLikeCount(productId, date, delta, updatedAt);
    }

    @Override
    @Transactional
    public void upsertOrderAmount(Long productId, LocalDate date, long amount, ZonedDateTime updatedAt) {
        jpaRepository.upsertOrderAmount(productId, date, amount, updatedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDailyMetrics> findByMetricDate(LocalDate date) {
        return jpaRepository.findByMetricDate(date);
    }

    @Override
    @Transactional
    public void bulkUpsert(List<DailyDelta> deltas) {
        if (deltas == null || deltas.isEmpty()) return;

        // Phase 1: 서로 다른 (productId, metric_date) 쌍에 대해 zero row 를 INSERT IGNORE 로 시딩
        //          → 이후 bulk UPSERT 의 INSERT 브랜치가 실행되지 않도록 보장
        bulkInsertIgnoreSeed(deltas);

        // Phase 2: VALUES(col) 에 raw delta 를 전달해 ON DUPLICATE KEY UPDATE 로 가감
        //          INSERT 브랜치는 Phase 1 에서 이미 row 가 존재하므로 실행되지 않는다.
        StringBuilder sb = new StringBuilder(
                "INSERT INTO product_daily_metrics " +
                        "(product_id, metric_date, view_count, like_count, order_amount, updated_at) VALUES ");
        for (int i = 0; i < deltas.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("(:pid").append(i)
                    .append(", :dt").append(i)
                    .append(", :vd").append(i)
                    .append(", :ld").append(i)
                    .append(", :od").append(i)
                    .append(", :ts").append(i).append(")");
        }
        sb.append(" ON DUPLICATE KEY UPDATE ")
                .append("view_count = GREATEST(view_count + VALUES(view_count), 0), ")
                .append("like_count = GREATEST(like_count + VALUES(like_count), 0), ")
                .append("order_amount = GREATEST(order_amount + VALUES(order_amount), 0), ")
                .append("updated_at = VALUES(updated_at)");

        Query q = em.createNativeQuery(sb.toString());
        for (int i = 0; i < deltas.size(); i++) {
            DailyDelta d = deltas.get(i);
            q.setParameter("pid" + i, d.productId());
            q.setParameter("dt" + i, Date.valueOf(d.date()));
            q.setParameter("vd" + i, d.viewDelta());
            q.setParameter("ld" + i, d.likeDelta());
            q.setParameter("od" + i, d.orderAmountDelta());
            q.setParameter("ts" + i, Timestamp.from(d.updatedAt().toInstant()));
        }
        q.executeUpdate();
    }

    private void bulkInsertIgnoreSeed(List<DailyDelta> deltas) {
        // 서로 다른 (pid, date) 쌍을 추려서 zero row 시딩
        record Key(Long pid, LocalDate date) {}
        Set<Key> distinct = new HashSet<>();
        for (DailyDelta d : deltas) {
            distinct.add(new Key(d.productId(), d.date()));
        }
        List<Key> keys = List.copyOf(distinct);

        StringBuilder sb = new StringBuilder(
                "INSERT IGNORE INTO product_daily_metrics " +
                        "(product_id, metric_date, view_count, like_count, order_amount, updated_at) VALUES ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("(:pid").append(i)
                    .append(", :dt").append(i)
                    .append(", 0, 0, 0, '2000-01-01 00:00:00')");
        }

        Query q = em.createNativeQuery(sb.toString());
        for (int i = 0; i < keys.size(); i++) {
            q.setParameter("pid" + i, keys.get(i).pid());
            q.setParameter("dt" + i, Date.valueOf(keys.get(i).date()));
        }
        q.executeUpdate();
    }
}
