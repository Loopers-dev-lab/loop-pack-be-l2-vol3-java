package com.loopers.domain.ranking.mv;

import java.util.List;

/**
 * {@code mv_product_rank_monthly} 접근 포트. 구현체는 infrastructure 레이어에 둔다.
 */
public interface ProductRankMonthlyRepository {

    void save(ProductRankMvRow row);

    List<ProductRankMvRow> findByPeriodKeyOrderByRankAsc(String periodKey);
}
