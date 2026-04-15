package com.loopers.application;

import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ScoreCarryOverScheduler 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ScoreCarryOverSchedulerTest {

    @InjectMocks
    private ScoreCarryOverScheduler scheduler;

    @Mock
    private RankingRepository rankingRepository;

    @Test
    @DisplayName("오늘 키에서 내일 키로 10% 가중치로 이월한다")
    void carriesOverWithTenPercentWeight() {
        // Act
        scheduler.carryOverScores();

        // Assert
        String todayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String tomorrowKey = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        ArgumentCaptor<Double> weightCaptor = ArgumentCaptor.forClass(Double.class);
        verify(rankingRepository).carryOverScores(
            org.mockito.ArgumentMatchers.eq(todayKey),
            org.mockito.ArgumentMatchers.eq(tomorrowKey),
            weightCaptor.capture()
        );
        assertThat(weightCaptor.getValue()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("이월 실패 시 예외를 던지지 않는다")
    void doesNotThrowOnFailure() {
        // Arrange
        doThrow(new RuntimeException("Redis 연결 실패"))
            .when(rankingRepository).carryOverScores(anyString(), anyString(), anyDouble());

        // Act & Assert - 예외가 발생하지 않아야 함
        scheduler.carryOverScores();
    }
}
