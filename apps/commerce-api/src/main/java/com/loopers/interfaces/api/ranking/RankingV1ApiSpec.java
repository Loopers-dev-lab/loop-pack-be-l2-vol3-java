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
            + "date 생략 시 오늘(Asia/Seoul)입니다. 실시간 갱신으로 재요청 시 항목이 달라질 수 있고, 목록의 rank와 상품 상세 rankingRank는 요청 시점이 달라 완전 일치를 보장하지 않습니다. "
            + "페이징·동점·원천 데이터 등 운영 고지는 design 문서 09-ranking-redis-zset-design.md를 참고합니다."
    )
    ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> getRankings(
        @Parameter(description = "기준 일자 yyyyMMdd (선택, 기본 오늘 Asia/Seoul)")
        String date,
        @Parameter(description = "페이지 번호 (1부터)")
        @Min(1) int page,
        @Parameter(description = "페이지 크기 (1~100)")
        @Min(1) @Max(100) int size
    );
}
