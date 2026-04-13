package com.loopers.application.ranking;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.RankingKeyConstants;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 랭킹 스코어 스냅샷 스케줄러.
 *
 * <p>5분 주기로 현재 시간 hourly Redis ZSET에서 상위 100개 상품의
 * score를 조회하여 DB에 upsert한다. Redis 장애 시 DB 스냅샷을
 * SOT(Source of Truth)로 활용하여 랭킹을 복구할 수 있는 기틀을 마련한다.</p>
 *
 * <p>스냅샷 간격 동안의 score 변화는 유실될 수 있으나,
 * 랭킹 특성상 허용 가능한 수준이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSnapshotScheduler {

    private static final int TOP_N = 100;
    private static final long SNAPSHOT_INTERVAL_MS = 300_000;

    private final RankingRepository rankingRepository;
    private final RankingSnapshotRepository rankingSnapshotRepository;

    /**
     * 주기적으로 현재 시간 hourly 키의 상위 랭킹을 DB에 스냅샷한다.
     *
     * <p>현재 시간 키에서 상위 {@value TOP_N}개 상품의 score를 조회하여
     * DB에 upsert한다. 데이터가 없으면 스킵한다.</p>
     */
    @Scheduled(fixedRate = SNAPSHOT_INTERVAL_MS)
    public void takeSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scoreHour = now.truncatedTo(ChronoUnit.HOURS);
        String currentHourKey = RankingKeyConstants.hourlyKey(now);

        List<RankingScore> topScores = rankingRepository.readTopScores(currentHourKey, TOP_N);
        if (topScores.isEmpty()) {
            log.debug("[Snapshot] 랭킹 데이터가 없습니다. key={}", currentHourKey);
            return;
        }

        rankingSnapshotRepository.saveAll(scoreHour, topScores);

        log.debug("[Snapshot] 스냅샷 완료: key={}, {}건", currentHourKey, topScores.size());
    }
}
