package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional(readOnly = true)
    public RankingInfo.RankingPageResponse getRankings(String date, int page, int size) {
        // 1. ZSET에서 Top-N 조회 (Redis)
        //    ZREVRANGE ranking:all:{date} {offset} {offset+size-1} WITHSCORES
        List<RankingEntry> entries = rankingService.getTopRankings(date, page, size);

        if (entries.isEmpty()) {
            return new RankingInfo.RankingPageResponse(List.of(), page, size);
        }

        // 2. productId 목록으로 상품 정보 일괄 조회 (DB)
        //    ZSET에는 productId만 있으므로, 상품명/가격/이미지 등은 DB에서 조회
        List<Long> productIds = entries.stream()
                .map(RankingEntry::productId)
                .toList();
        Map<Long, ProductModel> productMap = productService.getByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        // 3. 브랜드 정보 일괄 조회 (DB)
        //    상품의 brandId로 브랜드명을 가져온다 (N+1 방지를 위해 일괄 조회)
        Set<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, BrandModel> brandMap = brandService.getByIds(brandIds);

        // 4. ZSET 결과 + 상품 정보 + 브랜드 정보를 조합하여 응답 구성
        int baseRank = (page - 1) * size;  // 페이지별 기준 순위 (page=2, size=20이면 20)
        List<RankingInfo.RankingItem> rankings = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            RankingEntry entry = entries.get(i);
            ProductModel product = productMap.get(entry.productId());
            if (product == null) continue; // 삭제된 상품은 skip

            BrandModel brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : "Unknown";

            rankings.add(new RankingInfo.RankingItem(
                    baseRank + i + 1,
                    entry.score(),
                    product.getId(),
                    product.getName(),
                    brandName,
                    product.getPrice(),
                    product.getImageUrl()
            ));
        }

        return new RankingInfo.RankingPageResponse(rankings, page, size);
    }

    /**
     * DB ORDER BY 기반 랭킹 조회 — ZSET 성능 비교용.
     * product_metrics 테이블의 가중치 합산 점수로 정렬 후 상품/브랜드 Aggregation.
     */
    @Transactional(readOnly = true)
    public RankingInfo.RankingPageResponse getRankingsFromDB(int page, int size) {
        List<RankingEntry> entries = rankingService.getTopRankingsFromDB(page, size);

        if (entries.isEmpty()) {
            return new RankingInfo.RankingPageResponse(List.of(), page, size);
        }

        List<Long> productIds = entries.stream()
                .map(RankingEntry::productId)
                .toList();
        Map<Long, ProductModel> productMap = productService.getByIds(productIds).stream()
                .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        Set<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId)
                .collect(Collectors.toSet());
        Map<Long, BrandModel> brandMap = brandService.getByIds(brandIds);

        int baseRank = (page - 1) * size;
        List<RankingInfo.RankingItem> rankings = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            RankingEntry entry = entries.get(i);
            ProductModel product = productMap.get(entry.productId());
            if (product == null) continue;

            BrandModel brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : "Unknown";

            rankings.add(new RankingInfo.RankingItem(
                    baseRank + i + 1,
                    entry.score(),
                    product.getId(),
                    product.getName(),
                    brandName,
                    product.getPrice(),
                    product.getImageUrl()
            ));
        }

        return new RankingInfo.RankingPageResponse(rankings, page, size);
    }
}
