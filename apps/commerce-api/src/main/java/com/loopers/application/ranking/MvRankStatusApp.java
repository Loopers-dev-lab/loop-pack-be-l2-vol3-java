package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankPeriodType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MvRankStatusApp {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;

    public List<MvRankStatusInfo> listStatuses(RankPeriodType type, int offset, int size) {
        List<Object[]> publications = jdbcTemplate.query(
                "SELECT period_type, period_key, published_version, next_version, updated_at " +
                        "FROM mv_product_rank_publication WHERE period_type = ? " +
                        "ORDER BY period_key DESC LIMIT ? OFFSET ?",
                (rs, n) -> new Object[]{
                        rs.getString("period_type"),
                        rs.getString("period_key"),
                        rs.getLong("published_version"),
                        rs.getLong("next_version"),
                        rs.getTimestamp("updated_at")
                },
                type.name(), size, offset
        );
        List<MvRankStatusInfo> out = new ArrayList<>(publications.size());
        for (Object[] p : publications) {
            String periodKey = (String) p[1];
            long published = (long) p[2];
            out.add(buildStatus(type, periodKey, (String) p[0], published, (long) p[3], (Timestamp) p[4]));
        }
        return out;
    }

    public MvRankStatusInfo getStatus(RankPeriodType type, String periodKey) {
        List<Object[]> publications = jdbcTemplate.query(
                "SELECT period_type, period_key, published_version, next_version, updated_at " +
                        "FROM mv_product_rank_publication WHERE period_type = ? AND period_key = ?",
                (rs, n) -> new Object[]{
                        rs.getString("period_type"),
                        rs.getString("period_key"),
                        rs.getLong("published_version"),
                        rs.getLong("next_version"),
                        rs.getTimestamp("updated_at")
                },
                type.name(), periodKey
        );
        if (publications.isEmpty()) {
            return new MvRankStatusInfo(type.name(), periodKey, 0L, 0L, null, 0L, 0L, 0L, List.of());
        }
        Object[] p = publications.get(0);
        return buildStatus(type, periodKey, (String) p[0], (long) p[2], (long) p[3], (Timestamp) p[4]);
    }

    private MvRankStatusInfo buildStatus(RankPeriodType type, String periodKey, String pt,
                                          long published, long next, Timestamp updatedAt) {
        List<MvRankVersionCount> breakdown = jdbcTemplate.query(
                "SELECT version, COUNT(*) AS cnt FROM " + type.getTableName() +
                        " WHERE period_key = ? GROUP BY version ORDER BY version DESC",
                (rs, n) -> new MvRankVersionCount(rs.getLong("version"), rs.getLong("cnt")),
                periodKey
        );
        long total = breakdown.stream().mapToLong(MvRankVersionCount::rowCount).sum();
        long publishedRows = breakdown.stream()
                .filter(b -> b.version() == published)
                .mapToLong(MvRankVersionCount::rowCount)
                .sum();
        long orphan = total - publishedRows;
        ZonedDateTime ts = updatedAt == null ? null : updatedAt.toInstant().atZone(SEOUL);
        return new MvRankStatusInfo(pt, periodKey, published, next, ts, total, publishedRows, orphan, breakdown);
    }
}
