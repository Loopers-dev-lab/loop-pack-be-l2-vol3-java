package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Ranking API", description = "실시간 랭킹 조회 API")
public interface RankingV1ApiSpec {

    @Operation(summary = "Top-N 랭킹 조회 (offset 기반)", description = "period, date, page, size 파라미터로 Top-N 랭킹을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공 (빈 결과는 empty items)")
    })
    ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getRankingByOffset(
            @Parameter(description = "랭킹 기간 (daily|weekly|monthly). 생략 시 daily", example = "daily")
            @RequestParam(required = false) String period,
            @Parameter(description = "랭킹 날짜 (yyyyMMdd). 생략 시 오늘", example = "20260405")
            @RequestParam(required = false) String date,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "Top-N 랭킹 조회 (cursor 기반)", description = "date, cursor(이전 응답의 nextCursor), size로 일관된 페이징을 제공합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<ApiResponse<RankingV1Dto.RankingCursorResponse>> getRankingByCursor(
            @Parameter(description = "랭킹 날짜 (yyyyMMdd). 생략 시 오늘", example = "20260405")
            @RequestParam(required = false) String date,
            @Parameter(description = "이전 페이지의 nextCursor (score). 생략 시 첫 페이지")
            @RequestParam(required = false) Double cursor,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "시간 단위 Top-N 랭킹 조회 (offset 기반)", description = "1시간 단위 랭킹을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getHourlyRankingByOffset(
            @Parameter(description = "랭킹 날짜 (yyyyMMdd). 생략 시 오늘", example = "20260408")
            @RequestParam(required = false) String date,
            @Parameter(description = "시간 (0-23). 생략 시 현재 시간", example = "14")
            @RequestParam(required = false) Integer hour,
            @Parameter(description = "페이지 번호 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "시간 단위 Top-N 랭킹 조회 (cursor 기반)", description = "1시간 단위 cursor 페이징 랭킹을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<ApiResponse<RankingV1Dto.RankingCursorResponse>> getHourlyRankingByCursor(
            @Parameter(description = "랭킹 날짜 (yyyyMMdd). 생략 시 오늘", example = "20260408")
            @RequestParam(required = false) String date,
            @Parameter(description = "시간 (0-23). 생략 시 현재 시간", example = "14")
            @RequestParam(required = false) Integer hour,
            @Parameter(description = "이전 페이지의 nextCursor (score). 생략 시 첫 페이지")
            @RequestParam(required = false) Double cursor,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size
    );
}
