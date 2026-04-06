package com.loopers.application.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingEvent;
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

    private static final String KEY_PREFIX = "ranking:v1:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

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

        Map<Long, Double> scores = new HashMap<>();
        for (RankingEvent event : events) {
            if (!eventHandledRepository.markIfAbsent(event.eventId())) {
                log.debug("[Ranking] 중복 이벤트 skip: eventId={}", event.eventId());
                continue;
            }
            double score = switch (event) {
                case RankingEvent.View view -> scoreCalculator.calculateViewScore();
                case RankingEvent.Like like -> scoreCalculator.calculateLikeScore(like.liked());
                case RankingEvent.Order order -> scoreCalculator.calculateOrderScore(order.price(), order.quantity());
            };
            scores.merge(event.productId(), score, Double::sum);
        }
        scores.values().removeIf(score -> score == 0.0);
        if (!scores.isEmpty()) {
            rankingRepository.incrementScores(todayKey(), scores);
        }
    }

    private String todayKey() {
        return KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
    }
}
