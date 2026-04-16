package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductDailySignalModel;
import com.loopers.domain.ranking.ProductDailySignalRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.ranking.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingRecalculationApp {

    private final ProductDailySignalRepository productDailySignalRepository;
    private final ScoreCalculator scoreCalculator;
    private final RankingRepository rankingRepository;

    public long recalculate(LocalDate date) {
        List<ProductDailySignalModel> signals = productDailySignalRepository.findBySignalDate(date);
        if (signals.isEmpty()) {
            log.info("[RECALCULATE] date={} — 신호 데이터 없음, 스킵", date);
            return 0L;
        }

        Map<Long, Double> productScores = new HashMap<>();
        for (ProductDailySignalModel signal : signals) {
            double score = scoreCalculator.calculateTotal(
                    signal.getViewCount(),
                    signal.getLikeCount(),
                    signal.getOrderAmount().doubleValue()
            );
            productScores.put(signal.getProductDbId(), score);
        }

        rankingRepository.addAllToShadow(date, productScores);
        rankingRepository.renameShadowToMain(date);

        log.info("[RECALCULATE] date={}, products={} — 재집계 완료", date, productScores.size());
        return productScores.size();
    }
}
