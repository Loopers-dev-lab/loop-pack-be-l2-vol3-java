package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {

    private final RankingRepository rankingRepository;

    /**
     * 이벤트 기반 랭킹 점수를 증가시킨다. (조회, 주문용)
     */
    public void incrementScore(Long productId, double score, LocalDateTime occurredAt) {
        LocalDate date = resolveDate(occurredAt);
        rankingRepository.incrementScore(productId, score, date);
        rankingRepository.incrementHourlyScore(productId, score, occurredAt);
        log.debug("[Ranking] ZINCRBY productId={}, score={}, date={}", productId, score, date);
    }

    /**
     * 여러 상품의 점수를 Redis Pipeline으로 일괄 증가시킨다.
     *
     * @param productScores productId -> 합산 점수 맵
     */
    public void incrementScoreBatch(Map<Long, Double> productScores) {
        LocalDate today = LocalDate.now();
        rankingRepository.incrementScoreBatch(productScores, today);
        log.debug("[Ranking] Pipeline 배치 — {}개 상품 점수 적재", productScores.size());
    }

    /**
     * 좋아요 멱등 점수 증가 — SADD로 중복 확인 후 ZINCRBY.
     */
    public void incrementLikeScoreIfAbsent(Long productId, Long userId, double score,
                                            LocalDateTime occurredAt) {
        LocalDate date = resolveDate(occurredAt);
        boolean isNew = rankingRepository.addLikeIfAbsent(productId, userId, date);
        if (isNew) {
            rankingRepository.incrementScore(productId, score, date);
            rankingRepository.incrementHourlyScore(productId, score, occurredAt);
            log.debug("[Ranking] LIKED (new) productId={}, userId={}, score={}", productId, userId, score);
        } else {
            log.debug("[Ranking] LIKED (duplicate, skip) productId={}, userId={}", productId, userId);
        }
    }

    /**
     * 좋아요 취소 멱등 점수 차감 — SREM으로 확인 후 ZINCRBY 음수.
     */
    public void decrementLikeScoreIfPresent(Long productId, Long userId, double score,
                                             LocalDateTime occurredAt) {
        LocalDate date = resolveDate(occurredAt);
        boolean wasPresent = rankingRepository.removeLikeIfPresent(productId, userId, date);
        if (wasPresent) {
            rankingRepository.incrementScore(productId, -score, date);
            rankingRepository.incrementHourlyScore(productId, -score, occurredAt);
            log.debug("[Ranking] UNLIKED (removed) productId={}, userId={}, score=-{}", productId, userId, score);
        } else {
            log.debug("[Ranking] UNLIKED (not found, skip) productId={}, userId={}", productId, userId);
        }
    }

    private LocalDate resolveDate(LocalDateTime occurredAt) {
        return occurredAt != null ? occurredAt.toLocalDate() : LocalDate.now();
    }
}
