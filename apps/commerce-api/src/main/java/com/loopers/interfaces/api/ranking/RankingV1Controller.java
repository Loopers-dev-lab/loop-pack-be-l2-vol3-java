package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductReadModel;
import com.loopers.application.ranking.RankingQueryService;
import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
@Validated
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingQueryService rankingQueryService;
    private final ProductApplicationService productApplicationService;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getDailyRanking(
        @RequestParam(required = false) @Pattern(regexp = "\\d{8}", message = "날짜 형식은 yyyyMMdd여야 합니다.") String date,
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
        @RequestParam(defaultValue = "20") @Min(value = 1, message = "size는 1 이상이어야 합니다.") int size
    ) {
        String resolvedDate = (date != null) ? date : LocalDate.now(KST).format(DateTimeFormatter.ofPattern("uuuuMMdd"));
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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STRICT_DAY_FORMAT =
        DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    private void validateDate(String date) {
        try {
            LocalDate.parse(date, STRICT_DAY_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 날짜입니다: " + date);
        }
    }
}
