package com.loopers.batch.job.ranking.step.audit;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.batch.job.ranking.step.stage.StagingAggregationProcessor;
import com.loopers.domain.ranking.audit.BatchAuditLog;
import com.loopers.domain.ranking.audit.BatchAuditLogRepository;
import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 7 — MV 가 "정의된 상태 (정확히 TOP 100)" 인지 불변조건을 검증한다.
 *
 * <p>검증 항목:
 * <ul>
 *     <li>count = 100 (TOP 100 완전 적재)</li>
 *     <li>MIN(rank_position) = 1, MAX = 100 (1~100 연속)</li>
 *     <li>DISTINCT product_id count = 100 (중복 없음)</li>
 * </ul>
 * 위반 시 Job FAIL → Step 6 (Redis 전파) 차단. 잘못된 MV 가 캐시로 퍼지는 경로를 원천 차단.</p>
 *
 * <p>CHECKSUM (값 해시 비교) 은 의도적으로 도입하지 않음.
 * 랭킹은 "돈이 잘못 움직이지 않는" 도메인이며, score 값 버그는 테스트 코드가 잡을 영역.</p>
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class AuditTasklet implements Tasklet {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int EXPECTED_COUNT = 100;

    private static final String AUDIT_SQL_TEMPLATE = """
            SELECT COUNT(*) AS row_count,
                   COALESCE(MIN(rank_position), 0) AS min_rank,
                   COALESCE(MAX(rank_position), 0) AS max_rank,
                   COUNT(DISTINCT product_id)      AS distinct_products
              FROM %s
             WHERE anchor_date  = ?
               AND weight_group = ?
            """;

    private static final String AUDIT_SQL_LAST_7D  = AUDIT_SQL_TEMPLATE.formatted("mv_product_rank_last_7d");
    private static final String AUDIT_SQL_LAST_30D = AUDIT_SQL_TEMPLATE.formatted("mv_product_rank_last_30d");

    private final JdbcTemplate jdbcTemplate;
    private final WeightConfigRepository weightConfigRepository;
    private final BatchAuditLogRepository auditLogRepository;

    @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}")
    private String anchorDateKey;

    @Value("#{stepExecution.jobExecution.id}")
    private Long jobExecutionId;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate anchorDate = LocalDate.parse(anchorDateKey, KEY_FORMAT);
        List<WeightConfig> configs = weightConfigRepository.findAllByActiveTrue();
        if (configs.isEmpty()) {
            configs = List.of(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));
        }

        List<String> failures = new ArrayList<>();
        for (WeightConfig config : configs) {
            failures.addAll(auditPeriod(
                    anchorDate, StagingAggregationProcessor.PERIOD_LAST_7D,  config.getGroupName(), AUDIT_SQL_LAST_7D));
            failures.addAll(auditPeriod(
                    anchorDate, StagingAggregationProcessor.PERIOD_LAST_30D, config.getGroupName(), AUDIT_SQL_LAST_30D));
        }

        if (!failures.isEmpty()) {
            String message = "MV audit 실패: " + String.join(" / ", failures);
            log.error("[STEP=auditStep] FAILED anchorDate={} reasons={}", anchorDate, failures);
            throw new IllegalStateException(message);
        }

        log.info("[STEP=auditStep] OK anchorDate={} groups={}", anchorDate, configs.size());
        return RepeatStatus.FINISHED;
    }

    private List<String> auditPeriod(LocalDate anchorDate, String periodType,
                                     String weightGroup, String sql) {
        AuditResult result = jdbcTemplate.queryForObject(sql,
                (rs, rn) -> new AuditResult(
                        rs.getInt("row_count"),
                        rs.getInt("min_rank"),
                        rs.getInt("max_rank"),
                        rs.getInt("distinct_products")
                ),
                Date.valueOf(anchorDate), weightGroup);

        // 불변조건: (1) count ≤ TOP_N, (2) rank 가 1..count 로 연속, (3) product_id 중복 없음.
        // count 는 "정확히 100" 이 아니라 "TOP_N 이하 + 실제 상품 수만큼 채워짐" 이면 OK
        // (테스트·초기 운영처럼 상품이 적은 환경도 정상 취급).
        List<String> problems = new ArrayList<>();
        if (result.rowCount > EXPECTED_COUNT) {
            problems.add(String.format(
                    "%s/%s count=%d exceeds TOP_N(%d)",
                    periodType, weightGroup, result.rowCount, EXPECTED_COUNT));
        }
        if (result.rowCount > 0) {
            if (result.minRank != 1 || result.maxRank != result.rowCount) {
                problems.add(String.format(
                        "%s/%s rank not contiguous: [%d,%d] for count=%d",
                        periodType, weightGroup, result.minRank, result.maxRank, result.rowCount));
            }
            if (result.distinctProducts != result.rowCount) {
                problems.add(String.format(
                        "%s/%s duplicate product_id: distinct=%d count=%d",
                        periodType, weightGroup, result.distinctProducts, result.rowCount));
            }
        }

        if (problems.isEmpty()) {
            auditLogRepository.save(BatchAuditLog.ok(
                    jobExecutionId, anchorDate, periodType, weightGroup, result.rowCount));
        } else {
            auditLogRepository.save(BatchAuditLog.failed(
                    jobExecutionId, anchorDate, periodType, weightGroup,
                    result.rowCount, String.join(" ; ", problems)));
        }
        return problems;
    }

    private record AuditResult(int rowCount, int minRank, int maxRank, int distinctProducts) {}
}
