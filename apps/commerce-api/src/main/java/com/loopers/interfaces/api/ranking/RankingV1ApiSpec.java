package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

/**
 * 일간 인기 상품 랭킹 조회 API 정의
 * <p>
 * Redis ZSET 기반 일간 랭킹을 페이지로 조회합니다. date 생략 시 서울 기준 오늘 날짜입니다.
 */
@Tag(name = "Ranking V1 API", description = "일간 인기 상품 랭킹 조회 API (비로그인 허용)")
public interface RankingV1ApiSpec {

    @Operation(summary = "랭킹 목록 조회", description = "Redis ZSET 기반 일간 랭킹을 페이지로 조회합니다. date 생략 시 서울 기준 오늘 날짜입니다.")
    ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> getRankings(
            @Parameter(description = "일자 yyyyMMdd (선택, 기본: 오늘(Asia/Seoul))") String date,
            @Parameter(description = "페이지 (1부터)") @Min(1) int page,
            @Parameter(description = "페이지 크기") @Min(1) @Max(100) int size
    );
}
