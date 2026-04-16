package com.loopers.job.productranking;


import org.springframework.jdbc.core.JdbcTemplate;


final class ProductRankingBatchSchemaSupport {

	private ProductRankingBatchSchemaSupport() {
	}

	static void createTables(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS product_read_model (
			    id BIGINT NOT NULL,
			    brand_id BIGINT NOT NULL,
			    brand_name VARCHAR(100),
			    name VARCHAR(200) NOT NULL,
			    price DECIMAL(12, 2) NOT NULL,
			    stock BIGINT NOT NULL,
			    description VARCHAR(1000),
			    like_count BIGINT NOT NULL,
			    metrics_version BIGINT NOT NULL DEFAULT 0,
			    created_at DATETIME(6) NOT NULL,
			    updated_at DATETIME(6) NOT NULL,
			    deleted_at DATETIME(6),
			    PRIMARY KEY (id)
			)
			""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS product_metrics (
			    metric_date DATE NOT NULL,
			    product_id BIGINT NOT NULL,
			    like_count BIGINT NOT NULL DEFAULT 0,
			    sales_count BIGINT NOT NULL DEFAULT 0,
			    view_count BIGINT NOT NULL DEFAULT 0,
			    version BIGINT NOT NULL DEFAULT 0,
			    updated_at DATETIME(6) NOT NULL,
			    PRIMARY KEY (metric_date, product_id),
			    INDEX idx_product_metrics_product_date (product_id, metric_date)
			)
			""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
			    id BIGINT NOT NULL AUTO_INCREMENT,
			    week_start_date DATE NOT NULL,
			    week_end_date DATE NOT NULL,
			    scorer_type VARCHAR(30) NOT NULL,
			    rank_position INT NOT NULL,
			    score DECIMAL(18, 8) NOT NULL,
			    like_count BIGINT NOT NULL,
			    sales_count BIGINT NOT NULL,
			    view_count BIGINT NOT NULL,
			    product_id BIGINT NOT NULL,
			    product_name VARCHAR(200) NOT NULL,
			    brand_id BIGINT NOT NULL,
			    brand_name VARCHAR(100),
			    price DECIMAL(12, 2) NOT NULL,
			    created_at DATETIME(6) NOT NULL,
			    updated_at DATETIME(6) NOT NULL,
			    PRIMARY KEY (id),
			    UNIQUE KEY uk_weekly_period_product (week_start_date, scorer_type, product_id),
			    UNIQUE KEY uk_weekly_period_rank (week_start_date, scorer_type, rank_position),
			    INDEX idx_weekly_period_rank (week_start_date, scorer_type, rank_position)
			)
			""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS stg_product_rank_weekly (
			    job_execution_id BIGINT NOT NULL,
			    week_start_date DATE NOT NULL,
			    week_end_date DATE NOT NULL,
			    scorer_type VARCHAR(30) NOT NULL,
			    rank_position INT NOT NULL,
			    score DECIMAL(18, 8) NOT NULL,
			    like_count BIGINT NOT NULL,
			    sales_count BIGINT NOT NULL,
			    view_count BIGINT NOT NULL,
			    product_id BIGINT NOT NULL,
			    product_name VARCHAR(200) NOT NULL,
			    brand_id BIGINT NOT NULL,
			    brand_name VARCHAR(100),
			    price DECIMAL(12, 2) NOT NULL,
			    created_at DATETIME(6) NOT NULL,
			    updated_at DATETIME(6) NOT NULL,
			    PRIMARY KEY (job_execution_id, rank_position),
			    INDEX idx_stg_weekly_job (job_execution_id),
			    INDEX idx_stg_weekly_period (week_start_date, scorer_type)
			)
			""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
			    id BIGINT NOT NULL AUTO_INCREMENT,
			    month_start_date DATE NOT NULL,
			    month_key CHAR(7) NOT NULL,
			    scorer_type VARCHAR(30) NOT NULL,
			    rank_position INT NOT NULL,
			    score DECIMAL(18, 8) NOT NULL,
			    like_count BIGINT NOT NULL,
			    sales_count BIGINT NOT NULL,
			    view_count BIGINT NOT NULL,
			    product_id BIGINT NOT NULL,
			    product_name VARCHAR(200) NOT NULL,
			    brand_id BIGINT NOT NULL,
			    brand_name VARCHAR(100),
			    price DECIMAL(12, 2) NOT NULL,
			    created_at DATETIME(6) NOT NULL,
			    updated_at DATETIME(6) NOT NULL,
			    PRIMARY KEY (id),
			    UNIQUE KEY uk_monthly_period_product (month_start_date, scorer_type, product_id),
			    UNIQUE KEY uk_monthly_period_rank (month_start_date, scorer_type, rank_position),
			    INDEX idx_monthly_period_rank (month_start_date, scorer_type, rank_position)
			)
			""");

		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS stg_product_rank_monthly (
			    job_execution_id BIGINT NOT NULL,
			    month_start_date DATE NOT NULL,
			    month_key CHAR(7) NOT NULL,
			    scorer_type VARCHAR(30) NOT NULL,
			    rank_position INT NOT NULL,
			    score DECIMAL(18, 8) NOT NULL,
			    like_count BIGINT NOT NULL,
			    sales_count BIGINT NOT NULL,
			    view_count BIGINT NOT NULL,
			    product_id BIGINT NOT NULL,
			    product_name VARCHAR(200) NOT NULL,
			    brand_id BIGINT NOT NULL,
			    brand_name VARCHAR(100),
			    price DECIMAL(12, 2) NOT NULL,
			    created_at DATETIME(6) NOT NULL,
			    updated_at DATETIME(6) NOT NULL,
			    PRIMARY KEY (job_execution_id, rank_position),
			    INDEX idx_stg_monthly_job (job_execution_id),
			    INDEX idx_stg_monthly_period (month_start_date, scorer_type)
			)
			""");
	}

}
