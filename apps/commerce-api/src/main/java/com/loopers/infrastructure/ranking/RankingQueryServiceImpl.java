package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingQueryService;
import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.support.redis.RankingKeyConstants;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingQueryServiceImpl implements RankingQueryService {

    private final RankingRepository rankingRepository;

    @Override
    public PageResult<ProductRanking> getDailyRanking(String date, int page, int size) {
        try {
            String key = RankingKeyConstants.dayKey(date);
            long start = (long) page * size;
            long stop = start + size - 1;

            List<ProductRanking> rankings = rankingRepository.getTopN(key, start, stop);
            long totalElements = rankingRepository.getTotalCount(key);
            int totalPages = (int) Math.ceil((double) totalElements / size);

            return new PageResult<>(rankings, page, size, totalElements, totalPages);
        } catch (Exception e) {
            log.warn("[Ranking] Redis 조회 실패, 빈 결과 반환: {}", e.getMessage());
            return new PageResult<>(Collections.emptyList(), page, size, 0, 0);
        }
    }

    @Override
    public Optional<Long> getProductDailyRank(Long productId, String date) {
        try {
            String key = RankingKeyConstants.dayKey(date);
            return rankingRepository.getRank(key, productId);
        } catch (Exception e) {
            log.warn("[Ranking] Redis 순위 조회 실패, empty 반환: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
