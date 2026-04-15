package com.loopers.domain.ranking;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MvProductRankMonthlyId implements Serializable {
    private Long productId;
    private String yearMonth;
}
