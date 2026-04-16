package com.loopers.interfaces.api.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 조회",
        description = "period(DAILY/WEEKLY/MONTHLY) 와 기준일을 받아 해당 기간의 인기 상품 랭킹을 조회합니다. "
            + "date 미지정 시 오늘 날짜를 사용합니다. size 는 최대 100 까지 지원합니다. "
            + "\n\n**page 는 0-based** 입니다. 첫 페이지는 `?page=0`, 두 번째는 `?page=1`. "
            + "(Week 9 엔드포인트와의 호환성 유지를 위해 0-based 로 고정.) "
            + "\n\n**period 는 대문자로 전달해야 합니다.** 소문자/혼용(daily, Daily 등)은 400 BAD_REQUEST 로 거부됩니다. "
            + "허용 값: DAILY, WEEKLY, MONTHLY."
    )
    ApiResponse<RankingV1Dto.RankingPageResponse> getRanking(
        String date,
        RankingPeriod period,
        int page,
        int size
    );
}
