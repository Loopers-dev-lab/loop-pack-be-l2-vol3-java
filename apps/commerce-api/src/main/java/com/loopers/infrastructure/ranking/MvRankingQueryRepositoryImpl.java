package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.mv.MvRankEntry;
import com.loopers.domain.ranking.mv.MvRankingQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * JdbcTemplate 기반 MV 조회. JPA Entity 로 읽지 않는 이유:
 * - 응답에는 product_id/score/rank_position 만 필요 (나머지 컬럼 투영 불필요)
 * - 영속성 컨텍스트가 MV 를 관리할 가치 없음 (쓰기는 commerce-batch 가 소유)
 * - 단순 투영 SELECT 이므로 JDBC 가 가장 명료함 (설계.md "Writer 쪽 JPA 함정" 과 같은 결)
 */
@Repository
@RequiredArgsConstructor
public class MvRankingQueryRepositoryImpl implements MvRankingQueryRepository {

    private static final String SELECT_LAST_7D = """
            SELECT product_id, score, rank_position
              FROM mv_product_rank_last_7d
             WHERE anchor_date = ? AND weight_group = ?
             ORDER BY rank_position
             LIMIT ? OFFSET ?
            """;

    private static final String SELECT_LAST_30D = """
            SELECT product_id, score, rank_position
              FROM mv_product_rank_last_30d
             WHERE anchor_date = ? AND weight_group = ?
             ORDER BY rank_position
             LIMIT ? OFFSET ?
            """;

    private static final String COUNT_LAST_7D =
            "SELECT COUNT(*) FROM mv_product_rank_last_7d WHERE anchor_date = ? AND weight_group = ?";

    private static final String COUNT_LAST_30D =
            "SELECT COUNT(*) FROM mv_product_rank_last_30d WHERE anchor_date = ? AND weight_group = ?";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<MvRankEntry> findLast7d(LocalDate anchorDate, String weightGroup, int offset, int limit) {
        return jdbcTemplate.query(SELECT_LAST_7D,
                (rs, rn) -> new MvRankEntry(
                        rs.getLong("product_id"),
                        rs.getDouble("score"),
                        rs.getInt("rank_position")),
                Date.valueOf(anchorDate), weightGroup, limit, offset);
    }

    @Override
    public List<MvRankEntry> findLast30d(LocalDate anchorDate, String weightGroup, int offset, int limit) {
        return jdbcTemplate.query(SELECT_LAST_30D,
                (rs, rn) -> new MvRankEntry(
                        rs.getLong("product_id"),
                        rs.getDouble("score"),
                        rs.getInt("rank_position")),
                Date.valueOf(anchorDate), weightGroup, limit, offset);
    }

    @Override
    public long countLast7d(LocalDate anchorDate, String weightGroup) {
        Long c = jdbcTemplate.queryForObject(COUNT_LAST_7D, Long.class, Date.valueOf(anchorDate), weightGroup);
        return c == null ? 0L : c;
    }

    @Override
    public long countLast30d(LocalDate anchorDate, String weightGroup) {
        Long c = jdbcTemplate.queryForObject(COUNT_LAST_30D, Long.class, Date.valueOf(anchorDate), weightGroup);
        return c == null ? 0L : c;
    }
}
