package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvProductRankPublicationId;
import com.loopers.domain.ranking.MvProductRankPublicationModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MvProductRankPublicationJpaRepository
        extends JpaRepository<MvProductRankPublicationModel, MvProductRankPublicationId> {
}
