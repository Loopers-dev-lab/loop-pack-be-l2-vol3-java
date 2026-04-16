package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductReadModel;
import com.loopers.application.ranking.RankingQueryService;
import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STRICT_DAY_FORMAT =
        DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final int MAX_PAGE_SIZE = 100;

    private final RankingQueryService rankingQueryService;
    private final ProductApplicationService productApplicationService;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRanking(
        @RequestParam(required = false)
        @Pattern(regexp = "\\d{8}", message = "유효하지 않은 날짜 형식입니다.")
        String date,
        @RequestParam(defaultValue = "DAILY") RankingPeriod period,
        @RequestParam(defaultValue = "0")
        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        int page,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = MAX_PAGE_SIZE, message = "size는 " + MAX_PAGE_SIZE + " 이하여야 합니다.")
        int size
    ) {
        LocalDate baseDate = (date != null)
            ? parseDate(date)
            : LocalDate.now(KST);

        PageResult<ProductRanking> rankings =
            rankingQueryService.getRanking(period, baseDate, page, size);

        Set<Long> productIds = rankings.items().stream()
            .map(ProductRanking::productId)
            .collect(Collectors.toSet());

        Map<Long, ProductReadModel> productMap = productApplicationService.getByIds(productIds)
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> ProductReadModel.from(e.getValue())));

        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(rankings, productMap));
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, STRICT_DAY_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("ranking date parse failed. raw={}", date, e);
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 날짜입니다.", e);
        }
    }
}
