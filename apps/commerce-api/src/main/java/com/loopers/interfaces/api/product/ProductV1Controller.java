package com.loopers.interfaces.api.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductQueryService;
import com.loopers.application.product.ProductReadModel;
import com.loopers.application.ranking.RankingQueryService;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductV1Controller implements ProductV1ApiSpec {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd");

    private final ProductQueryService productQueryService;
    private final BrandApplicationService brandApplicationService;
    private final RankingQueryService rankingQueryService;

    @GetMapping
    @Override
    public ApiResponse<ProductV1Dto.ProductPageResponse> getAll(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<ProductReadModel> products = productQueryService.getAll(
            brandId, ProductSortType.from(sort), page, size
        );
        Set<Long> brandIds = products.items().stream()
            .map(ProductReadModel::brandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandApplicationService.getByIds(brandIds);
        return ApiResponse.success(ProductV1Dto.ProductPageResponse.from(products, brandMap));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductResponse> getById(@PathVariable Long productId) {
        ProductReadModel product = productQueryService.getById(productId);
        Brand brand = brandApplicationService.getById(product.brandId());
        String today = LocalDate.now(KST).format(DAY_FORMAT);
        Long rank = rankingQueryService.getProductDailyRank(productId, today).orElse(null);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(product, brand, rank));
    }
}
