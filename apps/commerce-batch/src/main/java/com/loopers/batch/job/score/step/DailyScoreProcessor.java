package com.loopers.batch.job.score.step;

import com.loopers.ranking.ScoreCalculator;
import com.loopers.domain.signal.ProductDailySignalModel;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;

import java.time.LocalDate;

@RequiredArgsConstructor
public class DailyScoreProcessor implements ItemProcessor<ProductDailySignalModel, MvProductScoreDailyRow> {

    private final ScoreCalculator calculator;
    private final LocalDate targetDate;

    @Override
    public MvProductScoreDailyRow process(ProductDailySignalModel signal) {
        double score = calculator.calculateTotal(
                signal.getViewCount(),
                signal.getLikeCount(),
                signal.getOrderAmount()
        );
        if (score <= 0.0) {
            return null;
        }
        return new MvProductScoreDailyRow(
                signal.getProductDbId(),
                targetDate,
                score,
                signal.getViewCount(),
                signal.getLikeCount(),
                signal.getOrderAmount()
        );
    }
}
