package com.loopers.application.ranking;

import static com.loopers.domain.ranking.RankingKeyConstants.DATE_FORMAT;
import static com.loopers.domain.ranking.RankingKeyConstants.KEY_PREFIX;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.domain.ranking.RankingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 랭킹 스코어 이월 스케줄러.
 *
 * <p>매일 23:50에 실행되어 오늘 상위 200개 상품의 스코어를
 * 감쇠 계수(0.01)를 적용한 뒤 내일 키에 이월한다.
 * 자정 전환 시 빈 랭킹(콜드 스타트) 문제를 방지한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCarryOverScheduler {

    private static final int TOP_N = 200;
    private static final double DECAY_FACTOR = 0.01;
    private static final long TTL_SECONDS = 172800;

    private final RankingRepository rankingRepository;

    /**
     * 매일 23:50에 오늘 상위 랭킹을 감쇠하여 내일 키에 이월한다.
     *
     * <p>내일 키가 이미 존재하면 중복 실행으로 판단하여 스킵한다.</p>
     */
    @Scheduled(cron = "0 50 23 * * *")
    public void carryOver() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        String todayKey = KEY_PREFIX + today.format(DATE_FORMAT);
        String tomorrowKey = KEY_PREFIX + tomorrow.format(DATE_FORMAT);

        if (rankingRepository.exists(tomorrowKey)) {
            log.debug("[CarryOver] 내일 키가 이미 존재합니다. 스킵합니다. key={}", tomorrowKey);
            return;
        }

        Map<String, Double> topScores = rankingRepository.readTopScores(todayKey, TOP_N);
        if (topScores.isEmpty()) {
            log.debug("[CarryOver] 오늘 랭킹 데이터가 없습니다. key={}", todayKey);
            return;
        }

        Map<String, Double> decayedScores = topScores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue() * DECAY_FACTOR));

        rankingRepository.addScores(tomorrowKey, decayedScores, TTL_SECONDS);

        log.debug("[CarryOver] 스코어 이월 완료: {} → {}, {}건", todayKey, tomorrowKey, decayedScores.size());
    }
}
