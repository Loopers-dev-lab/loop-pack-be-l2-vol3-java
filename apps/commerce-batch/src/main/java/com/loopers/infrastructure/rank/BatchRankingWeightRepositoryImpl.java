package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.BatchRankingWeightRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class BatchRankingWeightRepositoryImpl implements BatchRankingWeightRepository {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public BigDecimal findWeightByEventType(String eventType) {
        List<BigDecimal> results = jdbc.query(
            "SELECT weight FROM ranking_weight WHERE event_type = :eventType",
            Map.of("eventType", eventType),
            (rs, rowNum) -> rs.getBigDecimal("weight"));

        if (results.isEmpty()) {
            throw new IllegalStateException("ranking_weight 테이블에 eventType이 없음: " + eventType);
        }
        return results.get(0);
    }
}
