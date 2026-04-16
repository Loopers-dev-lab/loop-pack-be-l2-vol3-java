package com.loopers.infrastructure.persistence.jpa.rank;

import com.loopers.batch.domain.ProductRankWeekly;
import org.springframework.stereotype.Component;

/**
 * 주간 상품 랭킹 도메인 ↔ JPA 엔티티 매퍼.
 */
@Component
public class ProductRankWeeklyMapper {

    public ProductRankWeeklyJpaEntity toJpaEntity(ProductRankWeekly domain) {
        return new ProductRankWeeklyJpaEntity(
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

    public ProductRankWeekly toDomain(ProductRankWeeklyJpaEntity entity) {
        return ProductRankWeekly.reconstitute(
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