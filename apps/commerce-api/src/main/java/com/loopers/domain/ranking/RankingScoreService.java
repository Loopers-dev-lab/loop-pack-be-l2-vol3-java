package com.loopers.domain.ranking;

import com.loopers.domain.ranking.dto.RankingInfo;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RankingScoreService {

    private final ProductRankingScoreRepository rankingScoreRepository;

    @Transactional
    public void updateScore(Long productId, LocalDate rankingDate, RankingEventType eventType, int amount) {
        double delta = eventType.calculateScore(amount);
        ProductRankingScoreModel score = rankingScoreRepository
                .findByProductIdAndRankingDate(productId, rankingDate)
                .orElseGet(() -> rankingScoreRepository.save(
                        ProductRankingScoreModel.create(productId, rankingDate, 0)));

        score.addScore(delta);
    }

    @Transactional(readOnly = true)
    public List<RankingInfo.RankedScore> getTopRankedByDate(LocalDate date, Pageable pageable) {
        List<ProductRankingScoreModel> scores = rankingScoreRepository.findTopByRankingDateOrderByScoreDesc(date, pageable);
        return RankingInfo.RankedScore.fromScoresWithRank(
                scores, (int) pageable.getOffset());
    }

    @Transactional(readOnly = true)
    public long countByDate(LocalDate date) {
        return rankingScoreRepository.countByRankingDate(date);
    }

    @Transactional(readOnly = true)
    public Optional<Long> getRankByProductIdAndDate(Long productId, LocalDate date) {
        return rankingScoreRepository.findRankByProductIdAndRankingDate(productId, date);
    }

    @Transactional
    public void carryOver(LocalDate targetDate, double carryOverRate) {
        LocalDate previousDate = targetDate.minusDays(1);
        List<ProductRankingScoreModel> previousScores = rankingScoreRepository
                .findAllByRankingDate(previousDate);

        for (ProductRankingScoreModel previous : previousScores) {
            if (rankingScoreRepository.findByProductIdAndRankingDate(
                    previous.getProductId(), targetDate).isPresent()) {
                continue;
            }
            rankingScoreRepository.save(
                    previous.createCarriedOver(targetDate, carryOverRate));
        }
    }
}
