package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductReadModel;
import com.loopers.application.ranking.RankingQueryService;
import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STRICT_DAY_FORMAT =
        DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{8}");

    private final RankingQueryService rankingQueryService;
    private final ProductApplicationService productApplicationService;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getDailyRanking(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 0 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
        if (date != null && !DATE_PATTERN.matcher(date).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 날짜 형식입니다.");
        }

        String resolvedDate = (date != null) ? date : LocalDate.now(KST).format(STRICT_DAY_FORMAT);
        validateDate(resolvedDate);
        PageResult<ProductRanking> rankings = rankingQueryService.getDailyRanking(resolvedDate, page, size);

        Set<Long> productIds = rankings.items().stream()
            .map(ProductRanking::productId)
            .collect(Collectors.toSet());

        Map<Long, ProductReadModel> productMap = productApplicationService.getByIds(productIds)
            .entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> ProductReadModel.from(e.getValue())));

        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(rankings, productMap));
    }

    private void validateDate(String date) {
        try {
            LocalDate.parse(date, STRICT_DAY_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("ranking date parse failed. raw={}", date, e);
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 날짜입니다.", e);
        }
    }
}
