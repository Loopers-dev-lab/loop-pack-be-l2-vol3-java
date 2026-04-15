package com.loopers.application;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingWeight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class RankingService {

    static final DateTimeFormatter DATE_KEY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double TIE_BREAKER_DIVISOR = 1e13;

    private final RankingRepository rankingRepository;

    public void addScore(String eventType, Long productId, ZonedDateTime eventCreatedAt, double rawScore) {
        String dateKey = eventCreatedAt.format(DATE_KEY_FORMATTER);
        RankingWeight weight = RankingWeight.fromEventType(eventType);
        double weightedScore = weight.calculate(rawScore);
        double tieBreaker = eventCreatedAt.toEpochSecond() / TIE_BREAKER_DIVISOR;
        rankingRepository.incrementScore(dateKey, productId, weightedScore + tieBreaker);
    }
}
