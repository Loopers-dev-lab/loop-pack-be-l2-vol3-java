package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
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

    /**
     * 날짜 기반 랭킹 목록을 페이징으로 조회한다.
     * - ZSET에서 상위 N개 productId + score 조회
     * - productService.findAllByIds()로 일괄 IN 조회 (N+1 방지)
     * - ZSET에 있지만 삭제된 상품은 결과에서 제외
     *
     * @param date yyyyMMdd 형식. null 또는 빈 값이면 오늘 날짜 사용.
     */
    public List<RankingInfo> findRankings(String date, int page, int size) {
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
}
