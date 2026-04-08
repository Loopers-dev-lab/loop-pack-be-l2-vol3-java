package com.loopers.application.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 랭킹 점수 업데이트 — TX 커밋 후 실행
 *
 * ProductCacheManager의 registerEvictAfterCommit() 패턴을 따른다:
 * - DB TX 안에서 콜백을 등록하고, 커밋 확정 후에 Redis 작업을 실행
 * - TX 롤백 시 Redis 작업 미실행 → phantom/double increment 방지
 * - Redis 실패 시 best-effort 누락 (at-most-once, 랭킹 도메인에 적합)
 *
 * dual-write 문제에서 at-most-once를 선택한 근거:
 * - 랭킹은 일일 리셋 + 가중합 + 근사치 허용 도메인
 * - over-count(가짜 인기)가 under-count(소폭 누락)보다 나쁨
 */
@Component
public class RankingScoreUpdater {

    private static final Logger log = LoggerFactory.getLogger(RankingScoreUpdater.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingProperties rankingProperties;

    public RankingScoreUpdater(RankingRedisRepository rankingRedisRepository,
                                RankingProperties rankingProperties) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.rankingProperties = rankingProperties;
    }

    /**
     * TX 커밋 후 랭킹 점수 반영을 등록한다.
     *
     * @param eventType 이벤트 타입 (e.g., "ProductViewedEvent")
     * @param productId 대상 상품 ID
     */
    public void registerAfterCommit(String eventType, Long productId) {
        double delta = calculateDelta(eventType);
        if (delta == 0.0) {
            return;
        }

        String key = generateTodayKey();
        long ttlSeconds = TimeUnit.DAYS.toSeconds(rankingProperties.getTtlDays());

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            rankingRedisRepository.incrementScore(key, productId, delta, ttlSeconds);
                        } catch (Exception e) {
                            log.warn("[Ranking] 점수 반영 실패 — key={}, productId={}, delta={}, best-effort 누락",
                                    key, productId, delta, e);
                        }
                    }
                }
        );
    }

    private double calculateDelta(String eventType) {
        RankingProperties.Weights weights = rankingProperties.getWeights();
        return switch (eventType) {
            case "ProductViewedEvent" -> weights.getView();
            case "ProductLikedEvent" -> weights.getLike();
            case "ProductUnlikedEvent" -> weights.getUnlike();
            case "OrderItemSoldEvent" -> weights.getOrder();
            default -> 0.0;
        };
    }

    private String generateTodayKey() {
        String today = LocalDate.now(KST).format(DATE_FORMAT);
        return rankingProperties.getKeyPrefix() + ":" + today;
    }
}
