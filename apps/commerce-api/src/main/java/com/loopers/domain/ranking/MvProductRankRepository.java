package com.loopers.domain.ranking;

import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MvProductRankRepository {

    List<MvProductRank> findByPeriodKeyAndScope(String periodKey, String scope, Pageable pageable);

    long countByPeriodKeyAndScope(String periodKey, String scope);
}
