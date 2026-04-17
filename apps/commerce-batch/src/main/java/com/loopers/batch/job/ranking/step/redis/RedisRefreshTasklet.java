package com.loopers.batch.job.ranking.step.redis;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.weight.WeightConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 7 — MV 의 확정된 TOP 100 을 Redis ZSET identity cache 로 복제한다.
 *
 * <p>Shadow key 에 ZADD 후 RENAME 으로 원자적 교체 → 조회 중 깜빡임 없음.
 * Redis 는 MV 의 identity mirror (score·순서 동일) — 새 계산은 없다.</p>
 *
 * <p>Step 7 실패는 치명적이지 않음 — MV 자체는 영속되어 있고
 * 조회 API 가 MV fallback 으로 동일 응답을 만들 수 있음.</p>
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class RedisRefreshTasklet implements Tasklet {

    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration TTL = Duration.ofDays(3);

    private static final String SQL_LAST_7D = """
            SELECT product_id, score
              FROM mv_product_rank_last_7d
             WHERE anchor_date = ? AND weight_group = ?
             ORDER BY rank_position
            """;

    private static final String SQL_LAST_30D = """
            SELECT product_id, score
              FROM mv_product_rank_last_30d
             WHERE anchor_date = ? AND weight_group = ?
             ORDER BY rank_position
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}")
    private String anchorDateKey;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate anchorDate = LocalDate.parse(anchorDateKey, KEY_FORMAT);
        List<WeightConfig> configs = RankingJobParametersListener.restoreWeightConfigs(
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext());

        int totalAdded = 0;
        for (WeightConfig config : configs) {
            String group = config.getGroupName();
            totalAdded += refreshZSet(
                    "ranking:last7d:" + anchorDateKey + ":" + group,
                    SQL_LAST_7D, Date.valueOf(anchorDate), group);
            totalAdded += refreshZSet(
                    "ranking:last30d:" + anchorDateKey + ":" + group,
                    SQL_LAST_30D, Date.valueOf(anchorDate), group);
        }

        log.info("[STEP=redisRefreshStep] anchorDate={} groups={} zaddCount={}",
                anchorDate, configs.size(), totalAdded);
        return RepeatStatus.FINISHED;
    }

    private int refreshZSet(String key, String sql, Date anchorDate, String weightGroup) {
        List<ProductScore> rows = jdbcTemplate.query(sql,
                (rs, rn) -> new ProductScore(rs.getLong("product_id"), rs.getDouble("score")),
                anchorDate, weightGroup);

        if (rows.isEmpty()) {
            redisTemplate.delete(key);
            return 0;
        }

        String shadowKey = key + ":rebuild";
        redisTemplate.delete(shadowKey);

        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(rows.size());
        for (ProductScore row : rows) {
            tuples.add(ZSetOperations.TypedTuple.of(String.valueOf(row.productId()), row.score()));
        }
        redisTemplate.opsForZSet().add(shadowKey, tuples);
        redisTemplate.rename(shadowKey, key);
        redisTemplate.expire(key, TTL);

        return rows.size();
    }

    private record ProductScore(long productId, double score) {}
}
