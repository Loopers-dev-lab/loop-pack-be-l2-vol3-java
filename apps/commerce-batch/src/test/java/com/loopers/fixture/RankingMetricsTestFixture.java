package com.loopers.fixture;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class RankingMetricsTestFixture {

    private final NamedParameterJdbcTemplate jdbc;

    public RankingMetricsTestFixture(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertMetrics(LocalDate date, long productId, long viewCount, long likeCount, long orderRevenue) {
        jdbc.update("""
            INSERT INTO ranking_metrics
                (product_id, metrics_date, metrics_hour, view_count, like_count, order_revenue, dirty, created_at, updated_at)
            VALUES
                (:productId, :date, 0, :viewCount, :likeCount, :orderRevenue, false, NOW(), NOW())
            """,
            Map.of(
                "productId", productId,
                "date", date,
                "viewCount", viewCount,
                "likeCount", likeCount,
                "orderRevenue", orderRevenue
            ));
    }
}
