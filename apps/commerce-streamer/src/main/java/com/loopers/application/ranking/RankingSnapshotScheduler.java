package com.loopers.application.ranking;

import static com.loopers.domain.ranking.RankingKeyConstants.DATE_FORMAT;
import static com.loopers.domain.ranking.RankingKeyConstants.KEY_PREFIX;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 랭킹 스코어 스냅샷 스케줄러.
 *
 * <p>5분 주기로 Redis ZSET에서 상위 100개 상품의 score를 조회하여
 * DB에 upsert한다. Redis 장애 시 DB 스냅샷을 SOT(Source of Truth)로 활용하여 랭킹을 복구할 수 있는 기틀을 마련한다.</p>
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
     * 주기적으로 Redis 상위 랭킹을 DB에 스냅샷한다.
     *
     * <p>오늘 키에서 상위 {@value TOP_N}개 상품의 score를 조회하여
     * DB에 upsert한다. 데이터가 없으면 스킵한다.</p>
     */
    @Scheduled(fixedRate = SNAPSHOT_INTERVAL_MS)
    public void takeSnapshot() {
        LocalDate today = LocalDate.now();
        String todayKey = KEY_PREFIX + today.format(DATE_FORMAT);

        Map<String, Double> topScores = rankingRepository.readTopScores(todayKey, TOP_N);
        if (topScores.isEmpty()) {
            log.debug("[Snapshot] 랭킹 데이터가 없습니다. key={}", todayKey);
            return;
        }

        Map<Long, Double> productScores = topScores.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Long.parseLong(e.getKey()),
                        Map.Entry::getValue
                ));

        rankingSnapshotRepository.saveAll(today, productScores);

        log.debug("[Snapshot] 스냅샷 완료: key={}, {}건", todayKey, productScores.size());
    }
}
