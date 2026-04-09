package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.Map;

public interface RankingRepository {
    void incrementScore(LocalDate date, Long productDbId, double score);

    /**
     * {@code sourceDate}의 ZSET을 {@code destDate}의 ZSET으로 carry-over 한다.
     * ZUNIONSTORE로 score에 {@code weight}를 곱해서 저장하여 전날 상위 상품을 초기값으로 설정.
     *
     * @return carry-over된 멤버 수
     */
    long carryOver(LocalDate sourceDate, LocalDate destDate, double weight);

    void addAllToShadow(LocalDate date, Map<Long, Double> productScores);

    void renameShadowToMain(LocalDate date);

    long carryOverHourly(LocalDate date, int sourceHour, int destHour, double weight);
}
