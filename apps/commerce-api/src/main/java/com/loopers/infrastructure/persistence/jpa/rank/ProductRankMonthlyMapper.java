package com.loopers.infrastructure.persistence.jpa.rank;

import com.loopers.batch.domain.ProductRankMonthly;
import org.springframework.stereotype.Component;

/**
 * 월간 상품 랭킹 도메인 ↔ JPA 엔티티 매퍼.
 */
@Component
public class ProductRankMonthlyMapper {

    public ProductRankMonthlyJpaEntity toJpaEntity(ProductRankMonthly domain) {
        return new ProductRankMonthlyJpaEntity(
            domain.getProductId(),
            domain.getRankNumber(),
            domain.getTotalScore(),
            domain.getTotalViewCount(),
            domain.getTotalLikeCount(),
            domain.getTotalOrderCount(),
            domain.getPeriodStartDate(),
            domain.getPeriodEndDate()
        );
    }

    public ProductRankMonthly toDomain(ProductRankMonthlyJpaEntity entity) {
        return ProductRankMonthly.reconstitute(
            entity.getId(),
            entity.getProductId(),
            entity.getRankNumber(),
            entity.getTotalScore(),
            entity.getTotalViewCount(),
            entity.getTotalLikeCount(),
            entity.getTotalOrderCount(),
            entity.getPeriodStartDate(),
            entity.getPeriodEndDate(),
            entity.getCreatedAt()
        );
    }
}