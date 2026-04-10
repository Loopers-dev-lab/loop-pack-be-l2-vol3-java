package com.loopers.infrastructure.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RankingFallbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public Map<Long, Long> sumViewsByRange(LocalDateTime from, LocalDateTime to, int limit) {
        String sql = """
                SELECT product_id, SUM(view_count) AS total
                FROM product_view_metrics
                WHERE bucket_time >= ? AND bucket_time < ?
                GROUP BY product_id
                ORDER BY total DESC
                LIMIT ?
                """;
        return queryToMap(sql, from, to, limit);
    }

    public Map<Long, Long> sumLikesByRange(LocalDateTime from, LocalDateTime to, int limit) {
        String sql = """
                SELECT product_id, SUM(like_count) AS total
                FROM product_like_metrics
                WHERE bucket_time >= ? AND bucket_time < ?
                GROUP BY product_id
                ORDER BY total DESC
                LIMIT ?
                """;
        return queryToMap(sql, from, to, limit);
    }

    public Map<Long, Long> sumQuantityByRange(LocalDateTime from, LocalDateTime to, int limit) {
        String sql = """
                SELECT product_id, SUM(quantity) AS total
                FROM product_order_metrics
                WHERE bucket_time >= ? AND bucket_time < ?
                GROUP BY product_id
                ORDER BY total DESC
                LIMIT ?
                """;
        return queryToMap(sql, from, to, limit);
    }

    private Map<Long, Long> queryToMap(String sql, LocalDateTime from, LocalDateTime to, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, from, to, limit);
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("product_id")).longValue(),
                    ((Number) row.get("total")).longValue());
        }
        return result;
    }
}
