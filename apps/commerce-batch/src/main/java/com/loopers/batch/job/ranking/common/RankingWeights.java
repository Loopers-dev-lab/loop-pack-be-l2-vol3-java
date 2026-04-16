package com.loopers.batch.job.ranking.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랭킹 점수 가중치 (주간/월간 배치 집계용).
 *
 * <p>streamer의 {@code RankingWeight} 상수와 값이 일치해야 한다
 * (사용자가 일간/주간/월간 랭킹 결과의 기준이 동일하다고 인지하기 위함).</p>
 *
 * <p>yml prefix: {@code ranking.weights}</p>
 */
@ConfigurationProperties(prefix = "ranking.weights")
@Getter
@Setter
public class RankingWeights {

    private double like = 0.2;
    private double order = 0.7;
    private double view = 0.1;
}
