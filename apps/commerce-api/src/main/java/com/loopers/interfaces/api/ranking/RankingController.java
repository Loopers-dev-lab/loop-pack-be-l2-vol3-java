package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingQueryUseCase;
import com.loopers.domain.model.common.PageResult;
import com.loopers.interfaces.api.common.PageResponse;
import com.loopers.interfaces.api.ranking.dto.RankingItemResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final RankingQueryUseCase rankingQueryUseCase;

    public RankingController(RankingQueryUseCase rankingQueryUseCase) {
        this.rankingQueryUseCase = rankingQueryUseCase;
    }

    @GetMapping
    public ResponseEntity<PageResponse<RankingItemResponse>> getRankings(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "0") int page
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        PageResult<RankingQueryUseCase.RankingItemInfo> rankings =
                rankingQueryUseCase.getRankings(targetDate, page, size);

        return ResponseEntity.ok(PageResponse.from(rankings, RankingItemResponse::from));
    }
}
