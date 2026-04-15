package com.loopers.application;

import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DisplayName("RankingService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private RankingRepository rankingRepository;

    private static final ZonedDateTime EVENT_CREATED_AT = ZonedDateTime.parse("2026-04-07T10:00:00+09:00");

    @Test
    @DisplayName("PRODUCT_VIEWED 이벤트 시 VIEW 가중치(0.1) 기반 점수를 누적한다")
    void addsViewScore() {
        // Act
        rankingService.addScore("PRODUCT_VIEWED", 1L, EVENT_CREATED_AT, 1.0);

        // Assert
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq("20260407"), eq(1L), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isCloseTo(0.1, offset(0.001));
    }

    @Test
    @DisplayName("PRODUCT_LIKED 이벤트 시 LIKE 가중치(0.2) 기반 점수를 누적한다")
    void addsLikeScore() {
        // Act
        rankingService.addScore("PRODUCT_LIKED", 1L, EVENT_CREATED_AT, 1.0);

        // Assert
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq("20260407"), eq(1L), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isCloseTo(0.2, offset(0.001));
    }

    @Test
    @DisplayName("PRODUCT_UNLIKED 이벤트 시 음수 점수로 감소한다")
    void subtractsUnlikeScore() {
        // Act
        rankingService.addScore("PRODUCT_UNLIKED", 1L, EVENT_CREATED_AT, -1.0);

        // Assert
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq("20260407"), eq(1L), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isCloseTo(-0.2, offset(0.001));
    }

    @Test
    @DisplayName("ORDER_COMPLETED 이벤트 시 ORDER 가중치(0.6) 기반 점수를 누적한다")
    void addsOrderScore() {
        // Act
        rankingService.addScore("ORDER_COMPLETED", 1L, EVENT_CREATED_AT, 50000.0);

        // Assert
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq("20260407"), eq(1L), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isCloseTo(30000.0, offset(0.001));
    }

    @Test
    @DisplayName("이벤트 발생 날짜에 맞는 dateKey를 생성한다")
    void generatesCorrectDateKey() {
        // Arrange
        ZonedDateTime yesterday = ZonedDateTime.parse("2026-04-06T23:59:59+09:00");

        // Act
        rankingService.addScore("PRODUCT_VIEWED", 1L, yesterday, 1.0);

        // Assert
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq("20260406"), eq(1L), scoreCaptor.capture());
        assertThat(scoreCaptor.getValue()).isCloseTo(0.1, offset(0.001));
    }

    @Test
    @DisplayName("동일 가중치 점수에서 최신 이벤트가 더 높은 score를 가진다 (Tie-Breaker)")
    void newerEventHasHigherScore_whenSameWeightedScore() {
        // Arrange
        ZonedDateTime olderEvent = ZonedDateTime.parse("2026-04-07T09:00:00+09:00");
        ZonedDateTime newerEvent = ZonedDateTime.parse("2026-04-07T10:00:00+09:00");

        // Act
        rankingService.addScore("PRODUCT_VIEWED", 1L, olderEvent, 1.0);
        rankingService.addScore("PRODUCT_VIEWED", 2L, newerEvent, 1.0);

        // Assert
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq("20260407"), eq(1L), scoreCaptor.capture());
        double olderScore = scoreCaptor.getValue();

        ArgumentCaptor<Double> scoreCaptor2 = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).incrementScore(eq("20260407"), eq(2L), scoreCaptor2.capture());
        double newerScore = scoreCaptor2.getValue();

        assertThat(newerScore).isGreaterThan(olderScore);
    }
}
