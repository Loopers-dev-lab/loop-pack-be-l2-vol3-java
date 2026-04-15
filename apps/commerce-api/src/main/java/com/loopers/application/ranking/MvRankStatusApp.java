package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankPeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MvRankStatusApp {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final String SELECT_PUBLICATION_BASE =
            "SELECT period_type, period_key, published_version, next_version, updated_at " +
                    "FROM mv_product_rank_publication WHERE period_type = ?";

    private static final RowMapper<MvRankPublicationRow> PUBLICATION_MAPPER = (rs, n) -> new MvRankPublicationRow(
            rs.getString("period_type"),
            rs.getString("period_key"),
            rs.getLong("published_version"),
            rs.getLong("next_version"),
            rs.getTimestamp("updated_at")
    );

    private final JdbcTemplate jdbcTemplate;

    public List<MvRankStatusInfo> listStatuses(RankPeriodType type, int offset, int size) {
        return jdbcTemplate.query(
                        SELECT_PUBLICATION_BASE + " ORDER BY period_key DESC LIMIT ? OFFSET ?",
                        PUBLICATION_MAPPER, type.name(), size, offset
                ).stream()
                .map(r -> buildStatus(type, r))
                .toList();
    }

    public MvRankStatusInfo getStatus(RankPeriodType type, String periodKey) {
        return jdbcTemplate.query(
                        SELECT_PUBLICATION_BASE + " AND period_key = ?",
                        PUBLICATION_MAPPER, type.name(), periodKey
                ).stream().findFirst()
                .map(r -> buildStatus(type, r))
                .orElseGet(() -> new MvRankStatusInfo(type.name(), periodKey, 0L, 0L, null, 0L, 0L, 0L, List.of()));
    }

    private MvRankStatusInfo buildStatus(RankPeriodType type, MvRankPublicationRow row) {
        List<MvRankVersionCount> breakdown = jdbcTemplate.query(
                "SELECT version, COUNT(*) AS cnt FROM " + type.getTableName() +
                        " WHERE period_key = ? GROUP BY version ORDER BY version DESC",
                (rs, n) -> new MvRankVersionCount(rs.getLong("version"), rs.getLong("cnt")),
                row.periodKey()
        );
        long total = breakdown.stream().mapToLong(MvRankVersionCount::rowCount).sum();
        long publishedRows = breakdown.stream()
                .filter(b -> b.version() == row.publishedVersion())
                .mapToLong(MvRankVersionCount::rowCount)
                .sum();
        ZonedDateTime ts = row.updatedAt() == null ? null : row.updatedAt().toInstant().atZone(SEOUL);
        return new MvRankStatusInfo(row.periodType(), row.periodKey(),
                row.publishedVersion(), row.nextVersion(), ts,
                total, publishedRows, total - publishedRows, breakdown);
    }
}
