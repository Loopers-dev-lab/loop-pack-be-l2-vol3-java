package com.loopers.batch.job.productranking.monthly;


import com.loopers.batch.job.productranking.scorer.RankingScoreSqlExpressions;
import com.loopers.batch.job.productranking.weekly.dto.ProductRankAggregate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;


/**
 * 월간 랭킹 Reader 설정
 * - 주간 Reader와 동일 SQL 구조, month start/end를 바인딩하는 점만 다름
 */

@Configuration
public class MonthlyProductRankingReaderConfig {

	private static final String MONTHLY_RANKING_SQL = """
		WITH aggregated AS (
		    SELECT
		        pm.product_id,
		        SUM(pm.view_count)  AS view_count,
		        SUM(pm.like_count)  AS like_count,
		        SUM(pm.sales_count) AS sales_count,
		        prm.name            AS product_name,
		        prm.brand_id        AS brand_id,
		        prm.brand_name      AS brand_name,
		        prm.price           AS price
		    FROM product_metrics pm
		    JOIN product_read_model prm ON prm.id = pm.product_id
		    WHERE pm.metric_date BETWEEN ? AND ?
		      AND prm.deleted_at IS NULL
		    GROUP BY
		        pm.product_id,
		        prm.name,
		        prm.brand_id,
		        prm.brand_name,
		        prm.price
		),
		scored AS (
		    SELECT
		        aggregated.*,
		        (%s) AS score
		    FROM aggregated
		),
		ranked AS (
		    SELECT
		        scored.*,
		        ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC) AS rank_position
		    FROM scored
		)
		SELECT
		    product_id,
		    view_count,
		    like_count,
		    sales_count,
		    product_name,
		    brand_id,
		    brand_name,
		    price,
		    score,
		    rank_position
		FROM ranked
		WHERE rank_position <= 100
		ORDER BY rank_position ASC
		""";


	@Bean("monthlyProductRankingReader")
	@StepScope
	public JdbcCursorItemReader<ProductRankAggregate> monthlyProductRankingReader(
		DataSource dataSource,
		@Value("#{jobParameters['targetDate']}") String targetDate,
		@Value("#{jobParameters['scorerType'] ?: 'SATURATION'}") String scorerType
	) {
		LocalDate target = LocalDate.parse(targetDate);
		LocalDate monthStart = target.withDayOfMonth(1);
		LocalDate monthEnd = target.withDayOfMonth(target.lengthOfMonth());

		String scoreExpression = RankingScoreSqlExpressions.resolveScoreExpression(scorerType);
		String sql = String.format(MONTHLY_RANKING_SQL, scoreExpression);

		return new JdbcCursorItemReaderBuilder<ProductRankAggregate>()
			.name("monthlyProductRankingReader")
			.dataSource(dataSource)
			.sql(sql)
			.preparedStatementSetter(ps -> {
				ps.setDate(1, Date.valueOf(monthStart));
				ps.setDate(2, Date.valueOf(monthEnd));
			})
			.rowMapper((rs, rowNum) -> new ProductRankAggregate(
				rs.getLong("product_id"),
				rs.getLong("view_count"),
				rs.getLong("like_count"),
				rs.getLong("sales_count"),
				rs.getString("product_name"),
				rs.getLong("brand_id"),
				rs.getString("brand_name"),
				rs.getBigDecimal("price"),
				rs.getBigDecimal("score"),
				rs.getInt("rank_position")
			))
			.build();
	}

}
