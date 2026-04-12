package com.loopers.interfaces.scheduler;

import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingCarryOverSchedulerTest {

    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Test
    @DisplayName("스케줄러가 올바른 dateKey와 weight로 carryOver를 호출한다")
    void carryOverCallsRepositoryWithCorrectKeys() {
        RankingRepository rankingRepository = mock(RankingRepository.class);
        double weight = 0.1;
        RankingCarryOverScheduler scheduler = new RankingCarryOverScheduler(rankingRepository, weight);

        scheduler.carryOver();

        ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> weightCaptor = ArgumentCaptor.forClass(Double.class);

        verify(rankingRepository).carryOver(sourceCaptor.capture(), destCaptor.capture(), weightCaptor.capture());

        String expectedSource = LocalDate.now().minusDays(1).format(DATE_KEY_FORMAT);
        String expectedDest = LocalDate.now().format(DATE_KEY_FORMAT);

        assertThat(sourceCaptor.getValue()).isEqualTo(expectedSource);
        assertThat(destCaptor.getValue()).isEqualTo(expectedDest);
        assertThat(weightCaptor.getValue()).isEqualTo(0.1);
    }
}
