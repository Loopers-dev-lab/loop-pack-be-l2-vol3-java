package com.loopers.application.ranking;

import static com.loopers.domain.ranking.RankingKeyConstants.DATE_FORMAT;
import static com.loopers.domain.ranking.RankingKeyConstants.KEY_PREFIX;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingEvent;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingScoreCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 랭킹 이벤트 배치 처리 서비스.
 *
 * <p>이벤트 목록을 받아 중복 필터링 후 타입별 점수를 계산하고,
 * 상품별로 합산하여 Redis Sorted Set에 적재한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final EventHandledRepository eventHandledRepository;
    private final RankingScoreCalculator scoreCalculator;
    private final RankingRepository rankingRepository;

    /**
     * 랭킹 이벤트 배치를 처리한다.
     *
     * <p>이벤트별 멱등성 체크 후, 점수를 계산하여 상품별로 합산한다.
     * 합산 결과가 0.0인 항목은 제외하고 Redis에 적재한다.</p>
     *
     * @param events 랭킹 이벤트 목록
     */
    public void processBatch(List<RankingEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        List<RankingScore> scores = new ArrayList<>();
        for (RankingEvent event : filterDuplicates(events)) {
            switch (event) {
                case RankingEvent.View view -> scores.add(scoreCalculator.calculate(view));
                case RankingEvent.Like like -> scores.add(scoreCalculator.calculate(like));
                case RankingEvent.Order order -> scores.addAll(scoreCalculator.calculate(order));
                case RankingEvent.Delete ignored -> { }
            }
        }

        List<RankingScore> merged = RankingScore.mergeAll(scores);
        if (!merged.isEmpty()) {
            rankingRepository.incrementScores(todayKey(), merged);
        }
    }

    /**
     * 삭제된 상품을 랭킹에서 제거한다.
     *
     * <p>이벤트별 멱등성 체크 후, 오늘 키에서 제거하고
     * 내일 키가 존재하면 내일 키에서도 제거한다.</p>
     *
     * @param events 삭제 이벤트 목록
     */
    public void removeProducts(List<RankingEvent.Delete> events) {
        if (events.isEmpty()) {
            return;
        }

        List<Long> productIds = filterDuplicates(events).stream()
                .map(RankingEvent.Delete::productId)
                .toList();

        rankingRepository.removeMembers(todayKey(), productIds);
        rankingRepository.removeMembers(tomorrowKey(), productIds);
    }

    private <T extends RankingEvent> List<T> filterDuplicates(List<T> events) {
        if (events.isEmpty()) {
            return events;
        }

        List<T> filtered = new ArrayList<>();
        for (T event : events) {
            if (!eventHandledRepository.markIfAbsent(event.eventId())) {
                log.debug("[Ranking] 중복 이벤트 skip: eventId={}", event.eventId());
                continue;
            }
            filtered.add(event);
        }
        return filtered;
    }

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
    }

    private String tomorrowKey() {
        return KEY_PREFIX + LocalDate.now().plusDays(1).format(DATE_FORMAT);
    }
}
