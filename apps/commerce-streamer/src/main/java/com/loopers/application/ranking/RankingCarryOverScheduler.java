package com.loopers.application.ranking;

import static com.loopers.domain.ranking.RankingKeyConstants.DATE_FORMAT;
import static com.loopers.domain.ranking.RankingKeyConstants.DAILY_KEY_PREFIX;

import com.loopers.domain.ranking.RankingKeyConstants;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 랭킹 스코어 이월 스케줄러.
 *
 * <p>일간 이월: 매일 23:50에 오늘 daily 키의 상위 스코어를 감쇠하여 내일 키에 이월한다.</p>
 * <p>시간 이월: 매시 50분에 현재 hourly 키의 상위 스코어를 감쇠하여 다음 시간 키에 이월한다.</p>
 * <p>감쇠 계수(0.01)를 적용하여 콜드 스타트를 방지하되, 실제 이벤트가 유입되면 빠르게 대체된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final int TOP_N = 200;
    private static final double DECAY_FACTOR = 0.01;

    private final RankingRepository rankingRepository;

    /**
     * 매일 23:50에 오늘 daily 상위 랭킹을 감쇠하여 내일 키에 이월한다.
     *
     * <p>내일 키가 이미 존재하면 중복 실행으로 판단하여 스킵한다.</p>
     */
    @Scheduled(cron = "0 50 23 * * *")
    public void carryOverDaily() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = DAILY_KEY_PREFIX + today.format(DATE_FORMAT);
        String tomorrowKey = DAILY_KEY_PREFIX + tomorrow.format(DATE_FORMAT);

        doCarryOver(todayKey, tomorrowKey, "DailyCarryOver");
    }

    /**
     * 매시 50분에 현재 시간 hourly 상위 랭킹을 감쇠하여 다음 시간 키에 이월한다.
     *
     * <p>다음 시간 키가 이미 존재하면 중복 실행으로 판단하여 스킵한다.</p>
     */
    @Scheduled(cron = "0 50 * * * *")
    public void carryOverHourly() {
        String currentHourKey = RankingKeyConstants.currentHourKey();
        String nextHourKey = RankingKeyConstants.nextHourKey();

        doCarryOver(currentHourKey, nextHourKey, "HourlyCarryOver");
    }

    private void doCarryOver(String sourceKey, String targetKey, String label) {
        if (rankingRepository.exists(targetKey)) {
            log.debug("[{}] 대상 키가 이미 존재합니다. 스킵합니다. key={}", label, targetKey);
            return;
        }

        List<RankingScore> topScores = rankingRepository.readTopScores(sourceKey, TOP_N);
        if (topScores.isEmpty()) {
            log.debug("[{}] 원본 랭킹 데이터가 없습니다. key={}", label, sourceKey);
            return;
        }

        List<RankingScore> decayedScores = topScores.stream()
                .map(s -> s.decay(DECAY_FACTOR))
                .toList();

        long ttlSeconds = RankingKeyConstants.calculateTtlSeconds(targetKey);
        rankingRepository.addScores(targetKey, decayedScores, ttlSeconds);

        log.debug("[{}] 스코어 이월 완료: {} → {}, {}건", label, sourceKey, targetKey, decayedScores.size());
    }
}
