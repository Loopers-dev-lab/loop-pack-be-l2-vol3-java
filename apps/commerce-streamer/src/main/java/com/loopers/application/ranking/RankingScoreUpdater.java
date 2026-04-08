package com.loopers.application.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 랭킹 점수 계산 + 배치 flush
 *
 * 역할 전환 (R9 배치 정제):
 * - [Before] 건별 afterCommit 콜백 등록자 — TX 커밋마다 ZINCRBY 1회
 * - [After]  순수 delta 계산 + 배치 flush — Consumer가 합산한 Map을 받아 Pipeline 적재
 *
 * afterCommit을 제거한 근거:
 * - afterCommit은 건별 TX 커밋마다 Redis 호출 → 배치 합산 불가
 * - Consumer에서 process() 성공 = DB TX 커밋 완료 → delta 수집 시점이 안전
 * - at-most-once 보장 유지: process() 실패 시 delta 미수집
 *
 * 시간 단위 랭킹:
 * - flushBatch()에서 일간 키 + 시간 키 둘 다에 적재
 * - 시간 키 포맷: ranking:hourly:yyyyMMddHH (KST)
 */
@Component
public class RankingScoreUpdater {

    private static final Logger log = LoggerFactory.getLogger(RankingScoreUpdater.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingProperties rankingProperties;

    public RankingScoreUpdater(RankingRedisRepository rankingRedisRepository,
                                RankingProperties rankingProperties) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.rankingProperties = rankingProperties;
    }

    /**
     * 이벤트 타입에 대한 랭킹 delta를 계산한다 (순수 함수).
     *
     * @return delta 값. 알 수 없는 이벤트면 0.0
     */
    public double calculateDelta(String eventType) {
        RankingProperties.Weights weights = rankingProperties.getWeights();
        return switch (eventType) {
            case "ProductViewedEvent" -> weights.getView();
            case "ProductLikedEvent" -> weights.getLike();
            case "ProductUnlikedEvent" -> weights.getUnlike();
            case "OrderItemSoldEvent" -> weights.getOrder();
            default -> 0.0;
        };
    }

    /**
     * 배치 합산된 점수를 일간 + 시간 키에 일괄 적재한다.
     *
     * Consumer가 배치 루프에서 합산한 Map<productId, totalDelta>를 받아
     * Redis Pipeline으로 ZINCRBY를 실행한다.
     *
     * @param scores 상품별 합산 점수 (productId → totalDelta)
     */
    public void flushBatch(Map<Long, Double> scores) {
        if (scores.isEmpty()) {
            return;
        }

        try {
            String dailyKey = generateDailyKey();
            long dailyTtl = TimeUnit.DAYS.toSeconds(rankingProperties.getTtlDays());
            rankingRedisRepository.incrementScoreBatch(dailyKey, scores, dailyTtl);

            String hourlyKey = generateHourlyKey();
            long hourlyTtl = TimeUnit.HOURS.toSeconds(rankingProperties.getHourlyTtlHours());
            rankingRedisRepository.incrementScoreBatch(hourlyKey, scores, hourlyTtl);
        } catch (Exception e) {
            log.warn("[Ranking] 배치 flush 실패 — products={}건, best-effort 누락",
                    scores.size(), e);
        }
    }

    private String generateDailyKey() {
        String today = LocalDate.now(KST).format(DAILY_FORMAT);
        return rankingProperties.getKeyPrefix() + ":" + today;
    }

    private String generateHourlyKey() {
        String hour = LocalDateTime.now(KST).format(HOURLY_FORMAT);
        return rankingProperties.getHourlyKeyPrefix() + ":" + hour;
    }
}
