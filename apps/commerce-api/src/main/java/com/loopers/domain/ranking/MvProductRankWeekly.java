package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mv_product_rank_weekly")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankWeekly extends MvProductRank {
}
