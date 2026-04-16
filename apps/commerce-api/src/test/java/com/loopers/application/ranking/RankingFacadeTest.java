package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingFacade 단위 테스트")
class RankingFacadeTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 9);
    private static final Clock FIXED =
            Clock.fixed(TODAY.atStartOfDay(KST).plusHours(10).toInstant(), KST);

    private RankingRepository rankingRepository;
    private RankingAssembler rankingAssembler;
    private RankingFacade facade;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        rankingAssembler = mock(RankingAssembler.class);
        facade = new RankingFacade(rankingRepository, rankingAssembler, FIXED);
    }

    @Nested
    @DisplayName("getDailyRanking")
    class GetDailyRanking {

        @Test
        @DisplayName("date 가 null 이면 KST 오늘 날짜 키로 저장소를 조회한다")
        void nullDateDefaultsToToday() {
            // given
            when(rankingRepository.getTopN(any(), anyInt(), anyInt())).thenReturn(List.of());
            when(rankingAssembler.assemble(any(), anyLong(), any()))
                    .thenReturn(new RankingPageResult(TODAY, 0L, List.of()));

            // when
            facade.getDailyRanking(null, 1, 20);

            // then
            verify(rankingRepository).getTopN(eq("ranking:all:20260409"), eq(1), eq(20));
        }

        @Test
        @DisplayName("조회한 total 과 entries 를 RankingAssembler 에 위임한다")
        void delegatesToAssembler() {
            // given
            String key = "ranking:all:20260409";
            when(rankingRepository.getTotal(key)).thenReturn(5L);
            when(rankingRepository.getTopN(key, 1, 20)).thenReturn(List.of());
            when(rankingAssembler.assemble(TODAY, 5L, List.of()))
                    .thenReturn(new RankingPageResult(TODAY, 5L, List.of()));

            // when
            facade.getDailyRanking(TODAY, 1, 20);

            // then
            verify(rankingAssembler).assemble(TODAY, 5L, List.of());
        }
    }

    @Nested
    @DisplayName("getDailyRank")
    class GetDailyRank {

        @Test
        @DisplayName("ZREVRANK 결과를 그대로 반환")
        void returnsRank() {
            // given
            when(rankingRepository.getRank("ranking:all:20260409", 100L)).thenReturn(5L);

            // when
            Long rank = facade.getDailyRank(100L);

            // then
            assertThat(rank).isEqualTo(5L);
        }

        @Test
        @DisplayName("순위권 밖이면 null")
        void nullWhenAbsent() {
            // given
            when(rankingRepository.getRank(any(), any())).thenReturn(null);

            // when
            Long rank = facade.getDailyRank(100L);

            // then
            assertThat(rank).isNull();
        }

        @Test
        @DisplayName("productId 가 null 이면 null 반환")
        void nullProductId() {
            assertThat(facade.getDailyRank(null)).isNull();
        }
    }

    @Nested
    @DisplayName("KST 자정 경계값 — getDailyRank")
    class KstMidnightBoundary {

        @Test
        @DisplayName("KST 23:59:59 에는 그 날짜 키로 조회한다")
        void justBeforeMidnight_usesTodayKey() {
            // given — 2026-04-08 23:59:59 KST
            LocalDate kstDate = LocalDate.of(2026, 4, 8);
            Clock justBefore = Clock.fixed(
                    kstDate.atStartOfDay(KST).plusDays(1).minusSeconds(1).toInstant(), KST);
            RankingFacade facadeBefore = new RankingFacade(rankingRepository, rankingAssembler, justBefore);
            when(rankingRepository.getRank("ranking:all:20260408", 1L)).thenReturn(2L);

            // when
            Long rank = facadeBefore.getDailyRank(1L);

            // then
            assertThat(rank).isEqualTo(2L);
            verify(rankingRepository).getRank("ranking:all:20260408", 1L);
        }

        @Test
        @DisplayName("KST 00:00:00 에는 다음 날짜 키로 조회한다")
        void atMidnight_usesNextDayKey() {
            // given — 2026-04-09 00:00:00 KST
            Clock atMidnight = Clock.fixed(
                    TODAY.atStartOfDay(KST).toInstant(), KST);
            RankingFacade facadeAtMidnight = new RankingFacade(rankingRepository, rankingAssembler, atMidnight);
            when(rankingRepository.getRank("ranking:all:20260409", 1L)).thenReturn(1L);

            // when
            Long rank = facadeAtMidnight.getDailyRank(1L);

            // then
            assertThat(rank).isEqualTo(1L);
            verify(rankingRepository).getRank("ranking:all:20260409", 1L);
        }

        @Test
        @DisplayName("날짜 생략 케이스 — KST 23:59:59 에는 그 날 키로 getDailyRanking 을 호출한다")
        void getDailyRanking_justBeforeMidnight_usesTodayKey() {
            // given — 2026-04-08 23:59:59 KST
            LocalDate kstDate = LocalDate.of(2026, 4, 8);
            Clock justBefore = Clock.fixed(
                    kstDate.atStartOfDay(KST).plusDays(1).minusSeconds(1).toInstant(), KST);
            RankingFacade f = new RankingFacade(rankingRepository, rankingAssembler, justBefore);
            when(rankingRepository.getTopN(any(), anyInt(), anyInt())).thenReturn(List.of());

            // when
            f.getDailyRanking(null, 1, 20);

            // then
            verify(rankingRepository).getTopN(eq("ranking:all:20260408"), eq(1), eq(20));
        }

        @Test
        @DisplayName("날짜 생략 케이스 — KST 00:00:00 에는 새 날짜 키로 getDailyRanking 을 호출한다")
        void getDailyRanking_atMidnight_usesNextDayKey() {
            // given — 2026-04-09 00:00:00 KST
            Clock atMidnight = Clock.fixed(TODAY.atStartOfDay(KST).toInstant(), KST);
            RankingFacade f = new RankingFacade(rankingRepository, rankingAssembler, atMidnight);
            when(rankingRepository.getTopN(any(), anyInt(), anyInt())).thenReturn(List.of());

            // when
            f.getDailyRanking(null, 1, 20);

            // then
            verify(rankingRepository).getTopN(eq("ranking:all:20260409"), eq(1), eq(20));
        }
    }
}
