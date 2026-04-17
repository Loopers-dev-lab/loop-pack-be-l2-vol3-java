package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

@Tag(name = "Ranking V1 API", description = "인기 상품 랭킹 API: 일간(Redis)·주간/월간(MV) (비로그인 허용)")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 목록 조회",
        description = "일간: Redis ZSET(ranking:all:{yyyyMMdd}) 점수 내림차순, date 생략 시 오늘(Asia/Seoul). "
            + "`rankingSnapshotId`를 주면 POST /rankings/snapshots로 만든 스냅샷 ZSET에서만 페이징합니다. "
            + "주간/월간: `period=WEEKLY|MONTHLY`와 `periodKey`(주간 yyyyWww, 월간 yyyyMM)를 함께 지정하면 DB MV에서 조회합니다(일간과 date·스냅샷과 동시 사용 불가). "
            + "MV는 최대 100행 기준으로 total·페이징하며, 요청 page가 범위를 넘으면 빈 content와 total 유지. "
            + "응답 `dataSource`: REDIS·REDIS_SNAPSHOT·FALLBACK_LATEST·DEGRADED·MV_WEEKLY·MV_MONTHLY. "
            + "동일 값을 헤더 `X-Loopers-Ranking-Data-Source`로도 내려줍니다."
    )
    ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> getRankings(
        @Parameter(description = "기준 일자 yyyyMMdd (일간 전용, 선택, 기본 오늘 Asia/Seoul)")
        String date,
        @Parameter(description = "WEEKLY 또는 MONTHLY (주간/월간 MV, periodKey와 함께 지정)")
        String period,
        @Parameter(description = "주간 yyyyWww, 월간 yyyyMM")
        String periodKey,
        @Parameter(description = "페이지 번호 (1부터)")
        @Min(1) int page,
        @Parameter(description = "페이지 크기 (1~100)")
        @Min(1) @Max(100) int size,
        @Parameter(description = "스냅샷 UUID (일간 전용, POST /rankings/snapshots에서 발급)")
        String rankingSnapshotId
    );

    @Operation(summary = "랭킹 스냅샷 생성", description = "일간 ZSET을 복제한 키를 만들고 TTL을 건다. 이후 GET에 rankingSnapshotId로 넘겨 페이징한다.")
    ResponseEntity<ApiResponse<RankingV1Dto.SnapshotCreateResponse>> createRankingSnapshot(
        @Parameter(description = "기준 일자 yyyyMMdd (선택, 기본 오늘 Asia/Seoul)")
        String date
    );
}
