package com.loopers.batch.job.ranking.step.score;

import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import com.loopers.domain.ranking.staging.StagingRankingScored;
import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * StagingRankingAggregation 1건을 받아 활성 weight_group 수만큼 fan-out 한 StagingRankingScored 를 반환한다.
 *
 * <p>@BeforeStep 에서 WeightConfig 를 한 번 로드하여 Step 내내 DB 조회 없음 (Bulk).</p>
 * <p>score 는 WeightConfig 별로 다르게 계산된다 → A/B 테스트 그룹 지원.</p>
 */
@Component
@StepScope
@RequiredArgsConstructor
public class ScoreProcessor implements ItemProcessor<StagingRankingAggregation, List<StagingRankingScored>> {

    private final WeightConfigRepository weightConfigRepository;

    /**
     * 활성화된 weight_group 이 하나도 없을 때 사용하는 기본 설정.
     * streamer {@code RankingAggregator} 와 일관되게 "control" 그룹 기본값으로 fallback.
     */
    private static final WeightConfig DEFAULT_CONFIG =
            new WeightConfig("control", 0.1, 0.2, 0.7, 100, true);

    private List<WeightConfig> activeConfigs;

    @BeforeStep
    public void loadWeightConfigs(StepExecution stepExecution) {
        List<WeightConfig> loaded = weightConfigRepository.findAllByActiveTrue();
        this.activeConfigs = loaded.isEmpty() ? List.of(DEFAULT_CONFIG) : loaded;
    }

    @Override
    public List<StagingRankingScored> process(StagingRankingAggregation item) {
        List<StagingRankingScored> fanOut = new ArrayList<>(activeConfigs.size());
        for (WeightConfig config : activeConfigs) {
            double score = ScoreFormula.compute(
                    item.getViewCount(), item.getLikeCount(), item.getSalesAmount(), config
            );
            fanOut.add(new StagingRankingScored(
                    item.getPeriodType(),
                    item.getPeriodKey(),
                    config.getGroupName(),
                    item.getProductId(),
                    item.getViewCount(),
                    item.getLikeCount(),
                    item.getSalesAmount(),
                    score
            ));
        }
        return fanOut;
    }
}
