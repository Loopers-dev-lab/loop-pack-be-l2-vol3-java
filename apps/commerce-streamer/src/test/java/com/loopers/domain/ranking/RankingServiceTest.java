package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingService 단위 테스트")
class RankingServiceTest {

    @Mock
    private RankingRepository rankingRepository;

    @InjectMocks
    private RankingService rankingService;

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 4, 7, 14, 30);
    private static final LocalDate DATE = OCCURRED_AT.toLocalDate();

    @Test
    @DisplayName("incrementScore — occurredAt의 날짜로 ZINCRBY 호출")
    void incrementScore_UsesOccurredAtDate() {
        rankingService.incrementScore(101L, 0.1, OCCURRED_AT);

        verify(rankingRepository).incrementScore(101L, 0.1, DATE);
        verify(rankingRepository).incrementHourlyScore(101L, 0.1, OCCURRED_AT);
    }

    @Test
    @DisplayName("incrementScore — occurredAt이 null이면 오늘 날짜 사용")
    void incrementScore_FallbackToToday_WhenOccurredAtNull() {
        rankingService.incrementScore(101L, 0.1, null);

        verify(rankingRepository).incrementScore(eq(101L), eq(0.1), eq(LocalDate.now()));
        verify(rankingRepository).incrementHourlyScore(eq(101L), eq(0.1), isNull());
    }

    @Test
    @DisplayName("incrementLikeScoreIfAbsent — 신규 좋아요면 ZINCRBY 호출")
    void incrementLikeScoreIfAbsent_NewLike_IncrementsScore() {
        when(rankingRepository.addLikeIfAbsent(101L, 456L, DATE)).thenReturn(true);

        rankingService.incrementLikeScoreIfAbsent(101L, 456L, 0.2, OCCURRED_AT);

        verify(rankingRepository).incrementScore(101L, 0.2, DATE);
        verify(rankingRepository).incrementHourlyScore(101L, 0.2, OCCURRED_AT);
    }

    @Test
    @DisplayName("incrementLikeScoreIfAbsent — 중복 좋아요면 ZINCRBY 스킵")
    void incrementLikeScoreIfAbsent_DuplicateLike_SkipsIncrement() {
        when(rankingRepository.addLikeIfAbsent(101L, 456L, DATE)).thenReturn(false);

        rankingService.incrementLikeScoreIfAbsent(101L, 456L, 0.2, OCCURRED_AT);

        verify(rankingRepository, never()).incrementScore(anyLong(), anyDouble(), any());
        verify(rankingRepository, never()).incrementHourlyScore(anyLong(), anyDouble(), any());
    }

    @Test
    @DisplayName("decrementLikeScoreIfPresent — 좋아요가 있었으면 음수 ZINCRBY")
    void decrementLikeScoreIfPresent_WasPresent_DecrementsScore() {
        when(rankingRepository.removeLikeIfPresent(101L, 456L, DATE)).thenReturn(true);

        rankingService.decrementLikeScoreIfPresent(101L, 456L, 0.2, OCCURRED_AT);

        verify(rankingRepository).incrementScore(101L, -0.2, DATE);
        verify(rankingRepository).incrementHourlyScore(101L, -0.2, OCCURRED_AT);
    }

    @Test
    @DisplayName("decrementLikeScoreIfPresent — 좋아요가 없었으면 스킵")
    void decrementLikeScoreIfPresent_WasNotPresent_SkipsDecrement() {
        when(rankingRepository.removeLikeIfPresent(101L, 456L, DATE)).thenReturn(false);

        rankingService.decrementLikeScoreIfPresent(101L, 456L, 0.2, OCCURRED_AT);

        verify(rankingRepository, never()).incrementScore(anyLong(), anyDouble(), any());
        verify(rankingRepository, never()).incrementHourlyScore(anyLong(), anyDouble(), any());
    }
}
