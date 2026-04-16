package com.loopers.interfaces.api.batch;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Batch Admin V1 API", description = "배치 Job 실행 관리 API입니다.")
public interface BatchAdminV1ApiSpec {

    @Operation(
        summary = "주간 랭킹 집계 Job 실행",
        description = """
            주간 랭킹 집계 배치 Job을 실행합니다.
            targetDate가 속한 주(월~일)의 product_metrics_daily 데이터를 집계하여
            TOP 100 랭킹을 생성합니다.
            """
    )
    ApiResponse<BatchAdminV1Dto.JobExecutionResponse> runWeeklyRankingJob(
        @Parameter(description = "집계 대상 날짜 (yyyyMMdd 형식)", example = "20250414")
        String targetDate
    );

    @Operation(
        summary = "월간 랭킹 집계 Job 실행",
        description = """
            월간 랭킹 집계 배치 Job을 실행합니다.
            targetDate가 속한 월(1일~말일)의 product_metrics_daily 데이터를 집계하여
            TOP 100 랭킹을 생성합니다.
            """
    )
    ApiResponse<BatchAdminV1Dto.JobExecutionResponse> runMonthlyRankingJob(
        @Parameter(description = "집계 대상 날짜 (yyyyMMdd 형식)", example = "20250401")
        String targetDate
    );
}