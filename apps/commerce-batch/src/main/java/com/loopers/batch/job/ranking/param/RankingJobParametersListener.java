package com.loopers.batch.job.ranking.param;

import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Job 이 의존하는 모든 외부 입력을 최초 실행 시점에 ExecutionContext 에 동결한다.
 *
 * <p>동결 대상:
 * <ul>
 *     <li>{@code anchorDate} → 롤링 윈도우 경계 (last7dStart/End, last30dStart/End)</li>
 *     <li>{@code activeWeightGroups} → 활성 weight_group 이름 + 가중치 스냅샷</li>
 * </ul>
 *
 * <p>재시작 시 ExecutionContext 는 BATCH_JOB_EXECUTION_CONTEXT 테이블에서 복원되므로,
 * beforeJob 은 최초 실행 시에만 기록 (이미 값이 있으면 skip). 이로써 restart 사이에
 * ranking_weight_config 가 변경되어도 Job 은 최초 시작 시점의 스냅샷만 사용한다
 * (설계.md Bounded 원칙).</p>
 */
@Component
@RequiredArgsConstructor
public class RankingJobParametersListener implements JobExecutionListener {

    public static final String PARAM_ANCHOR_DATE = "anchorDate";

    // 롤링 윈도우 경계
    public static final String CTX_ANCHOR_DATE_KEY = "anchorDateKey";
    public static final String CTX_LAST_7D_START   = "last7dStart";
    public static final String CTX_LAST_7D_END     = "last7dEnd";
    public static final String CTX_LAST_30D_START  = "last30dStart";
    public static final String CTX_LAST_30D_END    = "last30dEnd";

    // weight_group 스냅샷
    public static final String CTX_ACTIVE_WEIGHT_GROUPS = "activeWeightGroups";
    private static final String CTX_WEIGHT_PREFIX = "w.";

    private final WeightConfigRepository weightConfigRepository;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        ExecutionContext ctx = jobExecution.getExecutionContext();
        if (ctx.containsKey(CTX_ANCHOR_DATE_KEY)) {
            return;
        }

        // 1. 롤링 윈도우 경계 동결
        String anchorDateParam = jobExecution.getJobParameters().getString(PARAM_ANCHOR_DATE);
        RollingWindow window = RollingWindowResolver.resolve(anchorDateParam);

        ctx.putString(CTX_ANCHOR_DATE_KEY, window.anchorDateKey());
        ctx.putString(CTX_LAST_7D_START,   window.last7dStart().toString());
        ctx.putString(CTX_LAST_7D_END,     window.last7dEnd().toString());
        ctx.putString(CTX_LAST_30D_START,  window.last30dStart().toString());
        ctx.putString(CTX_LAST_30D_END,    window.last30dEnd().toString());

        // 2. weight_group 스냅샷 동결
        List<WeightConfig> configs = weightConfigRepository.findAllByActiveTrue();
        if (configs.isEmpty()) {
            configs = List.of(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));
        }
        configs = configs.stream()
                .sorted(Comparator.comparing(WeightConfig::getGroupName))
                .toList();

        ctx.putString(CTX_ACTIVE_WEIGHT_GROUPS,
                configs.stream().map(WeightConfig::getGroupName).collect(Collectors.joining(",")));
        for (WeightConfig c : configs) {
            String prefix = CTX_WEIGHT_PREFIX + c.getGroupName() + ".";
            ctx.putDouble(prefix + "wView",  c.getWView());
            ctx.putDouble(prefix + "wLike",  c.getWLike());
            ctx.putDouble(prefix + "wOrder", c.getWOrder());
        }
    }

    /**
     * ExecutionContext 에서 동결된 weight_group 스냅샷을 복원한다.
     * Step 에서 DB 직접 조회 대신 이 메서드를 사용해야 Bounded 원칙이 유지된다.
     */
    public static List<WeightConfig> restoreWeightConfigs(ExecutionContext ctx) {
        String groups = ctx.getString(CTX_ACTIVE_WEIGHT_GROUPS);
        if (groups == null || groups.isBlank()) {
            return List.of(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));
        }
        List<WeightConfig> result = new ArrayList<>();
        for (String groupName : groups.split(",")) {
            String prefix = CTX_WEIGHT_PREFIX + groupName + ".";
            double wView  = ctx.getDouble(prefix + "wView");
            double wLike  = ctx.getDouble(prefix + "wLike");
            double wOrder = ctx.getDouble(prefix + "wOrder");
            result.add(new WeightConfig(groupName, wView, wLike, wOrder, 0, true));
        }
        return result;
    }
}
