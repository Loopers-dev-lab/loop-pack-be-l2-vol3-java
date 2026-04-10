package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingListInfo;
import com.loopers.domain.ranking.RankingSnapshotCreateResult;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/rankings")
@Validated
public class RankingV1Controller implements RankingV1ApiSpec {

    public static final String HEADER_RANKING_DATA_SOURCE = "X-Loopers-Ranking-Data-Source";

    private final RankingFacade rankingFacade;

    public RankingV1Controller(RankingFacade rankingFacade) {
        this.rankingFacade = rankingFacade;
    }

    /**
     * 일간 인기 상품 랭킹 조회 API 구현
     * <p>
     * 오프셋 페이징·실시간 재조회 시 변동 가능성은 {@link RankingV1ApiSpec} 및 design §4.2.6과 같다.
     *
     * @param date 일자 yyyyMMdd (선택, 기본: 오늘(Asia/Seoul))
     * @param page 오프셋 페이지 (1부터)
     * @param size 페이지 크기 (1~100)
     * @return 일간 인기 상품 랭킹 조회 결과
     */
    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String rankingSnapshotId
    ) {
        Optional<String> snap = Optional.ofNullable(rankingSnapshotId).filter(s -> !s.isBlank());
        RankingListInfo listResult = rankingFacade.getRankings(date, page, size, snap);
        return ResponseEntity.ok()
                .header(HEADER_RANKING_DATA_SOURCE, listResult.dataSource())
                .body(ApiResponse.success(RankingV1Dto.ListResponse.from(listResult)));
    }

    /**
     * 랭킹 스냅샷 생성 API 구현
     * @param date 일자 yyyyMMdd (선택, 기본: 오늘(Asia/Seoul))
     * @return 랭킹 스냅샷 생성 결과
     */
    @PostMapping("/snapshots")
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.SnapshotCreateResponse>> createRankingSnapshot(
            @RequestParam(required = false) String date
    ) {
        RankingSnapshotCreateResult created = rankingFacade.createRankingSnapshot(date);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(RankingV1Dto.SnapshotCreateResponse.from(created)));
    }
}
