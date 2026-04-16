package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.MvProductRankRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * MvProductRankRepository JDBC 구현체.
 *
 * DELETE + INSERT 전략으로 MV 테이블을 갱신한다.
 * 각 replace 메서드에 @Transactional 을 선언하여 DELETE 와 INSERT 의 원자성을
 * 호출 컨텍스트에 관계없이 Repository 스스로 보장한다.
 * Spring Batch Chunk 트랜잭션 안에서 호출될 때는 REQUIRED 전파로 기존 트랜잭션에 합류한다.
 *
 * UPSERT(INSERT ON DUPLICATE KEY UPDATE) 대신 DELETE + INSERT 를 선택한 이유:
 * - 같은 base_date 의 데이터를 완전히 교체하므로 잔여 행이 남지 않는다.
 * - 최대 100건(LIMIT 100) 의 소규모 배치이므로 락 경합이 문제되지 않는다.
 * - 코드가 단순하고 멱등성이 보장된다.
 *
 * 같은 base_date 로 재실행하면 기존 데이터를 삭제하고 새 랭킹으로 덮어쓴다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcMvProductRankRepository implements MvProductRankRepository {

    private static final String DELETE_WEEKLY =
            "DELETE FROM mv_product_rank_weekly WHERE base_date = ?";

    private static final String INSERT_WEEKLY =
            "INSERT INTO mv_product_rank_weekly (product_id, base_date, `rank`, score, updated_at) VALUES (?, ?, ?, ?, ?)";

    private static final String DELETE_MONTHLY =
            "DELETE FROM mv_product_rank_monthly WHERE base_date = ?";

    private static final String INSERT_MONTHLY =
            "INSERT INTO mv_product_rank_monthly (product_id, base_date, `rank`, score, updated_at) VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    @Override
    public void replaceWeeklyRanking(LocalDate baseDate, List<MvProductRankRow> rows) {
        Objects.requireNonNull(baseDate, "baseDate must not be null");
        Objects.requireNonNull(rows, "rows must not be null");
        jdbcTemplate.update(DELETE_WEEKLY, baseDate);
        insertBatch(INSERT_WEEKLY, baseDate, rows);
    }

    @Transactional
    @Override
    public void replaceMonthlyRanking(LocalDate baseDate, List<MvProductRankRow> rows) {
        Objects.requireNonNull(baseDate, "baseDate must not be null");
        Objects.requireNonNull(rows, "rows must not be null");
        jdbcTemplate.update(DELETE_MONTHLY, baseDate);
        insertBatch(INSERT_MONTHLY, baseDate, rows);
    }

    @Transactional
    @Override
    public void deleteWeeklyByBaseDate(LocalDate baseDate) {
        Objects.requireNonNull(baseDate, "baseDate must not be null");
        jdbcTemplate.update(DELETE_WEEKLY, baseDate);
    }

    @Transactional
    @Override
    public void deleteMonthlyByBaseDate(LocalDate baseDate) {
        Objects.requireNonNull(baseDate, "baseDate must not be null");
        jdbcTemplate.update(DELETE_MONTHLY, baseDate);
    }

    /**
     * rows 를 JDBC batchUpdate 로 한 번에 삽입한다.
     *
     * updated_at 은 배치 실행 시점 기준으로 일괄 설정한다.
     * rows 가 비어 있으면 batchUpdate 를 호출하지 않아도 되지만
     * JdbcTemplate 이 내부에서 빈 배치를 건너뛰므로 별도 처리가 필요 없다.
     */
    private void insertBatch(String sql, LocalDate baseDate, List<MvProductRankRow> rows) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.batchUpdate(sql, rows, rows.size(), (ps, row) -> {
            ps.setLong(1, row.productId());
            ps.setObject(2, baseDate);
            ps.setInt(3, row.rank());
            ps.setDouble(4, row.score());
            ps.setObject(5, now);
        });
    }
}
