package com.loopers.application.ranking;

import static com.loopers.domain.ranking.RankingKeyConstants.DATE_FORMAT;
import static com.loopers.domain.ranking.RankingKeyConstants.DAILY_KEY_PREFIX;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.ranking.RankingKeyConstants;
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
 * 상품별로 합산하여 일간 또는 시간 단위 Redis Sorted Set에 적재한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final EventHandledRepository eventHandledRepository;
    private final RankingScoreCalculator scoreCalculator;
    private final RankingRepository rankingRepository;

    /**
     * 랭킹 이벤트 배치를 처리하여 일간 키에 적재한다.
     *
     * @param events 랭킹 이벤트 목록
     */
    public void processDailyBatch(List<RankingEvent> events) {
        List<RankingScore> merged = calculateScores(events);
        if (!merged.isEmpty()) {
            rankingRepository.incrementScores(todayKey(), merged);
        }
    }

    /**
     * 랭킹 이벤트 배치를 처리하여 현재 시간 hourly 키에 적재한다.
     *
     * @param events 랭킹 이벤트 목록
     */
    public void processHourlyBatch(List<RankingEvent> events) {
        List<RankingScore> merged = calculateScores(events);
        if (!merged.isEmpty()) {
            rankingRepository.incrementScores(RankingKeyConstants.currentHourKey(), merged);
        }
    }

    /**
     * 삭제된 상품을 일간 랭킹에서 제거한다.
     *
     * @param events 삭제 이벤트 목록
     */
    public void removeDailyProducts(List<RankingEvent.Delete> events) {
        List<Long> productIds = filterDeleteEvents(events);
        if (productIds.isEmpty()) {
            return;
        }
        rankingRepository.removeMembers(todayKey(), productIds);
        rankingRepository.removeMembers(tomorrowKey(), productIds);
    }

    /**
     * 삭제된 상품을 시간 단위 랭킹에서 제거한다.
     *
     * @param events 삭제 이벤트 목록
     */
    public void removeHourlyProducts(List<RankingEvent.Delete> events) {
        List<Long> productIds = filterDeleteEvents(events);
        if (productIds.isEmpty()) {
            return;
        }
        rankingRepository.removeMembers(RankingKeyConstants.currentHourKey(), productIds);
        rankingRepository.removeMembers(RankingKeyConstants.nextHourKey(), productIds);
    }

    private List<RankingScore> calculateScores(List<RankingEvent> events) {
        if (events.isEmpty()) {
            return List.of();
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
        return RankingScore.mergeAll(scores);
    }

    private List<Long> filterDeleteEvents(List<RankingEvent.Delete> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        return filterDuplicates(events).stream()
                .map(RankingEvent.Delete::productId)
                .toList();
    }

    private <T extends RankingEvent> List<T> filterDuplicates(List<T> events) {
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
        return DAILY_KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
    }

    private String tomorrowKey() {
        return DAILY_KEY_PREFIX + LocalDate.now().plusDays(1).format(DATE_FORMAT);
    }
}
