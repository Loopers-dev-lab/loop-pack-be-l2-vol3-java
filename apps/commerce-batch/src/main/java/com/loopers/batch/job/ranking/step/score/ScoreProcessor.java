package com.loopers.batch.job.ranking.step.score;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import com.loopers.domain.ranking.staging.StagingRankingScored;
import com.loopers.domain.ranking.weight.WeightConfig;
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
 * <p>weight_group 은 DB 가 아닌 JobExecutionContext 의 스냅샷에서 로드한다.
 * Job 최초 시작 시점에 동결된 값이므로 restart 사이에 ranking_weight_config 가 변경되어도
 * fan-out 결과가 흐트러지지 않는다 (Bounded).</p>
 */
@Component
@StepScope
public class ScoreProcessor implements ItemProcessor<StagingRankingAggregation, List<StagingRankingScored>> {

    private List<WeightConfig> activeConfigs;

    @BeforeStep
    public void loadWeightConfigs(StepExecution stepExecution) {
        this.activeConfigs = RankingJobParametersListener.restoreWeightConfigs(
                stepExecution.getJobExecution().getExecutionContext());
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
