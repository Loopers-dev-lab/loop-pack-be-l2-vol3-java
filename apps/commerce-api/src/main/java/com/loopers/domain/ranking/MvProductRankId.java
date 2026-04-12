package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MvProductRankId implements Serializable {

    @Column(name = "period_key", nullable = false, length = 8)
    private String periodKey;

    @Column(name = "rank_no", nullable = false)
    private Integer rankNo;
}
