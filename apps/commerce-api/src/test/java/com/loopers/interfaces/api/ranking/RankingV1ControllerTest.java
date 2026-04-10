package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingListInfo;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.interfaces.api.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingV1ControllerTest {

    @Mock
    private RankingFacade rankingFacade;

    @InjectMocks
    private RankingV1Controller rankingV1Controller;

    @Test
    @DisplayName("fallback 목록 응답 시 헤더와 본문 dataSource가 FALLBACK_LATEST로 일치한다")
    void getRankings_whenFallbackLatest_shouldExposeSameDataSourceInHeaderAndBody() {
        RankingListInfo fallback = new RankingListInfo(
                List.of(),
                1,
                20,
                0L,
                0,
                "FALLBACK_LATEST",
                null
        );
        when(rankingFacade.getRankings("20260408", 1, 20, Optional.empty())).thenReturn(fallback);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response =
                rankingV1Controller.getRankings("20260408", 1, 20, null);

        assertThat(response.getHeaders().getFirst(RankingV1Controller.HEADER_RANKING_DATA_SOURCE))
                .isEqualTo("FALLBACK_LATEST");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().dataSource()).isEqualTo("FALLBACK_LATEST");
    }

    @Test
    @DisplayName("degraded 빈 목록 응답 시 헤더와 본문 dataSource가 DEGRADED로 일치한다")
    void getRankings_whenDegraded_shouldExposeSameDataSourceInHeaderAndBody() {
        RankingListInfo degraded = new RankingListInfo(
                List.of(),
                1,
                20,
                0L,
                0,
                "DEGRADED",
                null
        );
        when(rankingFacade.getRankings(null, 1, 20, Optional.empty())).thenReturn(degraded);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> response =
                rankingV1Controller.getRankings(null, 1, 20, null);

        assertThat(response.getHeaders().getFirst(RankingV1Controller.HEADER_RANKING_DATA_SOURCE))
                .isEqualTo("DEGRADED");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().dataSource()).isEqualTo("DEGRADED");
    }
}
