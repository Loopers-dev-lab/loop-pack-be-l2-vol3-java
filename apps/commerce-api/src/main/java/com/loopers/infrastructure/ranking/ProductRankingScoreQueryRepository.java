package com.loopers.infrastructure.ranking;

import static com.loopers.domain.ranking.QProductRankingScoreModel.productRankingScoreModel;

import com.loopers.domain.ranking.ProductRankingScoreModel;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRankingScoreQueryRepository {

    private final JPAQueryFactory queryFactory;

    public List<ProductRankingScoreModel> findTopByRankingDateOrderByScoreDesc(
            LocalDate rankingDate, Pageable pageable) {
        return queryFactory
                .selectFrom(productRankingScoreModel)
                .where(productRankingScoreModel.rankingDate.eq(rankingDate))
                .orderBy(productRankingScoreModel.score.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    public Optional<Long> findRankByProductIdAndRankingDate(
            Long productId, LocalDate rankingDate) {
        Double targetScore = queryFactory
                .select(productRankingScoreModel.score)
                .from(productRankingScoreModel)
                .where(productRankingScoreModel.productId.eq(productId)
                        .and(productRankingScoreModel.rankingDate.eq(rankingDate)))
                .fetchOne();

        if (targetScore == null) {
            return Optional.empty();
        }

        Long rank = queryFactory
                .select(productRankingScoreModel.count())
                .from(productRankingScoreModel)
                .where(productRankingScoreModel.rankingDate.eq(rankingDate)
                        .and(productRankingScoreModel.score.gt(targetScore)))
                .fetchOne();

        return Optional.of((rank != null ? rank : 0L) + 1);
    }

    public List<ProductRankingScoreModel> findAllByRankingDate(LocalDate rankingDate) {
        return queryFactory
                .selectFrom(productRankingScoreModel)
                .where(productRankingScoreModel.rankingDate.eq(rankingDate))
                .fetch();
    }
}
