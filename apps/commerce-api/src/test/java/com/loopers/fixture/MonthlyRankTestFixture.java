package com.loopers.fixture;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class MonthlyRankTestFixture {

    private final NamedParameterJdbcTemplate jdbc;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    public MonthlyRankTestFixture(NamedParameterJdbcTemplate jdbc,
                                   BrandJpaRepository brandJpaRepository,
                                   ProductJpaRepository productJpaRepository) {
        this.jdbc = jdbc;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    public void insert(LocalDate snapshotDate, long productId, int rank) {
        jdbc.update("""
            INSERT INTO mv_product_rank_monthly
                (snapshot_date, product_id, rank_position, score, view_count, like_count, order_revenue, created_at)
            VALUES
                (:snapshotDate, :productId, :rank, :score, :viewCount, :likeCount, :orderRevenue, NOW(6))
            """, new MapSqlParameterSource()
                .addValue("snapshotDate", snapshotDate)
                .addValue("productId", productId)
                .addValue("rank", rank)
                .addValue("score", 100.0 / rank)
                .addValue("viewCount", 100L)
                .addValue("likeCount", 10L)
                .addValue("orderRevenue", new BigDecimal("1000.00")));
    }

    public long insertWithProduct(LocalDate snapshotDate, long ignoredProductId, int rank) {
        Brand brand = brandJpaRepository.save(new Brand("테스트브랜드"));
        Product product = productJpaRepository.save(
            new Product(brand.getId(), "테스트상품" + rank, new Money(10000), new Stock(100)));
        insert(snapshotDate, product.getId(), rank);
        return product.getId();
    }
}
