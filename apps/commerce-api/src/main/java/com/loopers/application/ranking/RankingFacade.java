package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.ProductRankSnapshotQueryRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingType;
import com.loopers.event.ranking.RankingKeyGenerator;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class RankingFacade {
    private final RankingRepository rankingRepository;
    private final ProductRankSnapshotQueryRepository snapshotQueryRepository;
    private final ProductFacade productFacade;
    private final Clock clock;

    public RankingPageResult getRankings(LocalDate date, RankingType rankingType, int page, int size) {
        if (rankingType == RankingType.DAILY) {
            return getDailyRankings(date, page, size);
        }

        return getSnapshotRankings(rankingType, date, page, size);
    }

    public RankingInfo getProductRank(Long productId) {
        LocalDate today = LocalDate.now(clock);
        String key = RankingKeyGenerator.keyOf(today);
        Long rank = rankingRepository.getRank(key, productId);
        if (rank == null) {
            return null;
        }

        Double score = rankingRepository.getScore(key, productId);
        return new RankingInfo(rank + 1, score);
    }

    private RankingPageResult getDailyRankings(LocalDate date, int page, int size) {
        String key = RankingKeyGenerator.keyOf(date);
        int offset = (page - 1) * size;
        List<RankingEntry> entries = rankingRepository.getTopRankings(key, offset, size);

        List<RankingProductInfo> items = entries.stream()
                                                .map(entry -> toRankingProductInfo(entry).orElse(null))
                                                .filter(Objects::nonNull)
                                                .toList();

        return new RankingPageResult(items, page, size);
    }

    private RankingPageResult getSnapshotRankings(RankingType rankingType, LocalDate date, int page, int size) {
        LocalDate rankDate = (date != null) ? date
                                            : snapshotQueryRepository.findLatestRankDate(rankingType).orElse(null);

        if (rankDate == null) {
            return new RankingPageResult(List.of(), page, size);
        }

        int offset = (page - 1) * size;
        List<ProductRankSnapshot> snapshots = snapshotQueryRepository.findRankings(rankingType, rankDate, offset, size);

        List<RankingProductInfo> items = snapshots.stream()
                                                  .map(this::toRankingProductInfo)
                                                  .toList();

        return new RankingPageResult(items, page, size);
    }

    private RankingProductInfo toRankingProductInfo(ProductRankSnapshot snapshot) {
        return new RankingProductInfo(
                snapshot.getProductId(),
                snapshot.getProductName(),
                snapshot.getPrice(),
                snapshot.getBrandName(),
                (long) snapshot.getRankPosition(),
                snapshot.getScore()
        );
    }

    private Optional<RankingProductInfo> toRankingProductInfo(RankingEntry entry) {
        try {
            ProductInfo product = productFacade.getActiveProduct(entry.productId());
            return Optional.of(RankingProductInfo.of(product, entry.rank() + 1, entry.score()));
        } catch (CoreException e) {
            log.warn("랭킹 조회 중 상품 조회 실패 [productId={}]: {}", entry.productId(), e.getMessage());
            return Optional.empty();
        }
    }
}
