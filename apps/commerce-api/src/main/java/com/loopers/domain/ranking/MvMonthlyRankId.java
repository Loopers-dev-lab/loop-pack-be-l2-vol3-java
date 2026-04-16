package com.loopers.domain.ranking;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MvMonthlyRankId implements Serializable {

    private String yearMonth;
    private Long productId;
}
