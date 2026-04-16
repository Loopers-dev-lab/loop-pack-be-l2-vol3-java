package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankMonthlyRepository;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.MvProductRankWeeklyRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRedisRepository rankingRedisRepository;
    private final ProductService productService;
    private final MvProductRankWeeklyRepository mvWeeklyRepository;
    private final MvProductRankMonthlyRepository mvMonthlyRepository;

    /**
     * period(daily|weekly|monthly) 기반으로 랭킹 목록을 조회한다.
     * - daily (기본값): Redis ZSET 조회 (date 파라미터 기반)
     * - weekly: mv_product_rank_weekly 조회
     * - monthly: mv_product_rank_monthly 조회
     *
     * @param period "daily" | "weekly" | "monthly". null 또는 기타 값이면 "daily" 로 처리.
     * @param date   yyyyMMdd 형식. period=daily 일 때만 사용. null 이면 오늘.
     */
    public List<RankingInfo> findRankings(String period, String date, int page, int size) {
        if ("weekly".equalsIgnoreCase(period)) {
            return findWeeklyRankings(page, size);
        }
        if ("monthly".equalsIgnoreCase(period)) {
            return findMonthlyRankings(page, size);
        }
        return findDailyRankings(date, page, size);
    }

    private List<RankingInfo> findDailyRankings(String date, int page, int size) {
        LocalDate targetDate = (date == null || date.isBlank())
            ? LocalDate.now()
            : LocalDate.parse(date, DATE_FORMATTER);

        List<ZSetOperations.TypedTuple<String>> tuples = rankingRedisRepository.findTopN(targetDate, page, size);
        if (tuples.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = tuples.stream()
            .map(t -> Long.parseLong(t.getValue()))
            .toList();

        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<RankingInfo> result = new ArrayList<>();
        int baseRank = page * size + 1;
        for (int i = 0; i < tuples.size(); i++) {
            ZSetOperations.TypedTuple<String> tuple = tuples.get(i);
            Long productId = Long.parseLong(tuple.getValue());
            Product product = productMap.get(productId);
            if (product == null) {
                continue;
            }
            double score = tuple.getScore() != null ? tuple.getScore() : 0.0;
            result.add(RankingInfo.of(baseRank + i, product, score));
        }
        return result;
    }

    private List<RankingInfo> findWeeklyRankings(int page, int size) {
        List<MvProductRankWeekly> rows = mvWeeklyRepository.findTop(page, size);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = rows.stream().map(MvProductRankWeekly::getProductId).toList();
        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<RankingInfo> result = new ArrayList<>();
        int baseRank = page * size + 1;
        for (int i = 0; i < rows.size(); i++) {
            MvProductRankWeekly row = rows.get(i);
            Product product = productMap.get(row.getProductId());
            if (product == null) {
                continue;
            }
            result.add(RankingInfo.of(baseRank + i, product, row.getScore()));
        }
        return result;
    }

    private List<RankingInfo> findMonthlyRankings(int page, int size) {
        List<MvProductRankMonthly> rows = mvMonthlyRepository.findTop(page, size);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = rows.stream().map(MvProductRankMonthly::getProductId).toList();
        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<RankingInfo> result = new ArrayList<>();
        int baseRank = page * size + 1;
        for (int i = 0; i < rows.size(); i++) {
            MvProductRankMonthly row = rows.get(i);
            Product product = productMap.get(row.getProductId());
            if (product == null) {
                continue;
            }
            result.add(RankingInfo.of(baseRank + i, product, row.getScore()));
        }
        return result;
    }
}
