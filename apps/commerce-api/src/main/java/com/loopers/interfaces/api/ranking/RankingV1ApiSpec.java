package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

@Tag(name = "Ranking V1 API", description = "일간 인기 상품 랭킹 API (비로그인 허용)")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 목록 조회",
        description = "지정 일자의 일간 랭킹을 page·size 오프셋으로 조회합니다. Redis ZSET(ranking:all:{yyyyMMdd}) 점수 내림차순이며, "
            + "date 생략 시 오늘(Asia/Seoul)입니다. `rankingSnapshotId`를 주면 POST /rankings/snapshots로 만든 스냅샷 ZSET에서만 페이징해 순서가 고정됩니다. "
            + "라이브 조회는 실시간 갱신으로 재요청 시 항목이 달라질 수 있습니다. "
            + "응답 `dataSource`는 REDIS·REDIS_SNAPSHOT·FALLBACK_LATEST·DEGRADED를 구분합니다. "
            + "동일 값을 헤더 `X-Loopers-Ranking-Data-Source`로도 내려줍니다."
    )
    ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> getRankings(
        @Parameter(description = "기준 일자 yyyyMMdd (선택, 기본 오늘 Asia/Seoul)")
        String date,
        @Parameter(description = "페이지 번호 (1부터)")
        @Min(1) int page,
        @Parameter(description = "페이지 크기 (1~100)")
        @Min(1) @Max(100) int size,
        @Parameter(description = "스냅샷 UUID (선택, POST /rankings/snapshots에서 발급)")
        String rankingSnapshotId
    );

    @Operation(summary = "랭킹 스냅샷 생성", description = "일간 ZSET을 복제한 키를 만들고 TTL을 건다. 이후 GET에 rankingSnapshotId로 넘겨 페이징한다.")
    ResponseEntity<ApiResponse<RankingV1Dto.SnapshotCreateResponse>> createRankingSnapshot(
        @Parameter(description = "기준 일자 yyyyMMdd (선택, 기본 오늘 Asia/Seoul)")
        String date
    );
}
