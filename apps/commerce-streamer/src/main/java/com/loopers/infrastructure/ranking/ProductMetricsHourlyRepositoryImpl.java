package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductDailyAggregate;
import com.loopers.domain.ranking.ProductMetricsHourlyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * `ProductMetricsHourlyRepository` 의 JPA/Native 기반 구현.
 *
 * - 쓰기: Native `INSERT ... ON DUPLICATE KEY UPDATE` (MySQL 전용 문법)
 *   → 동시 UPSERT race 방지, 음수 clamp 포함.
 * - 읽기: Native `SELECT SUM(...) WHERE bucket_hour BETWEEN ... GROUP BY product_id`
 *   → 일자 전체 버킷을 단일 쿼리로 합산.
 */
@Component
@RequiredArgsConstructor
public class ProductMetricsHourlyRepositoryImpl implements ProductMetricsHourlyRepository {

    private final EntityManager entityManager;

    @Override
    public void upsertIncrements(
            Long productId,
            LocalDateTime bucketHour,
            long viewDelta,
            long likeDelta,
            long orderDelta,
            BigDecimal amountDelta
    ) {
        BigDecimal safeAmount = amountDelta == null ? BigDecimal.ZERO : amountDelta;

        Query query = entityManager.createNativeQuery("""
                INSERT INTO product_metrics_hourly
                    (product_id, bucket_hour, view_count, like_count, order_count, order_amount, updated_at)
                VALUES
                    (:pid, :bucket, GREATEST(:vd, 0), GREATEST(:ld, 0), GREATEST(:od, 0), GREATEST(:ad, 0), NOW())
                ON DUPLICATE KEY UPDATE
                    view_count   = GREATEST(view_count + :vd, 0),
                    like_count   = GREATEST(like_count + :ld, 0),
                    order_count  = GREATEST(order_count + :od, 0),
                    order_amount = GREATEST(order_amount + :ad, 0),
                    updated_at   = NOW()
                """);
        query.setParameter("pid", productId);
        query.setParameter("bucket", bucketHour);
        query.setParameter("vd", viewDelta);
        query.setParameter("ld", likeDelta);
        query.setParameter("od", orderDelta);
        query.setParameter("ad", safeAmount);
        query.executeUpdate();
    }

    @Override
    public ProductDailyAggregate snapshotByDate(Long productId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        Query query = entityManager.createNativeQuery("""
                SELECT
                    COALESCE(SUM(view_count),   0) AS total_view,
                    COALESCE(SUM(like_count),   0) AS total_like,
                    COALESCE(SUM(order_count),  0) AS total_order,
                    COALESCE(SUM(order_amount), 0) AS total_order_amount
                FROM product_metrics_hourly
                WHERE product_id = :pid
                  AND bucket_hour >= :start
                  AND bucket_hour <  :end
                """);
        query.setParameter("pid", productId);
        query.setParameter("start", start);
        query.setParameter("end", end);

        Object[] row = (Object[]) query.getSingleResult();
        long totalView = toLong(row[0]);
        long totalLike = toLong(row[1]);
        long totalOrder = toLong(row[2]);
        BigDecimal totalAmount = toBigDecimal(row[3]);

        return new ProductDailyAggregate(productId, totalView, totalLike, totalOrder, totalAmount);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Long, ProductDailyAggregate> snapshotsByDate(Set<Long> productIds, LocalDate date) {
        if (productIds == null || productIds.isEmpty()) return Map.of();

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        Query query = entityManager.createNativeQuery("""
                SELECT
                    product_id,
                    COALESCE(SUM(view_count),   0) AS total_view,
                    COALESCE(SUM(like_count),   0) AS total_like,
                    COALESCE(SUM(order_count),  0) AS total_order,
                    COALESCE(SUM(order_amount), 0) AS total_order_amount
                FROM product_metrics_hourly
                WHERE product_id IN (:ids)
                  AND bucket_hour >= :start
                  AND bucket_hour <  :end
                GROUP BY product_id
                """);
        query.setParameter("ids", productIds);
        query.setParameter("start", start);
        query.setParameter("end", end);

        List<Object[]> rows = query.getResultList();
        Map<Long, ProductDailyAggregate> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Long productId = toLong(row[0]);
            result.put(productId, new ProductDailyAggregate(
                    productId,
                    toLong(row[1]),
                    toLong(row[2]),
                    toLong(row[3]),
                    toBigDecimal(row[4])
            ));
        }

        // 조회 결과에 없는 상품은 0 스냅샷으로 채운다
        for (Long productId : productIds) {
            result.putIfAbsent(productId,
                    new ProductDailyAggregate(productId, 0L, 0L, 0L, BigDecimal.ZERO));
        }
        return result;
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }
}
