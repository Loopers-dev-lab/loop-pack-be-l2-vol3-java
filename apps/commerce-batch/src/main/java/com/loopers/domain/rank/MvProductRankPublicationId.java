package com.loopers.domain.rank;

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
public class MvProductRankPublicationId implements Serializable {

    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType;

    @Column(name = "period_key", nullable = false, length = 8)
    private String periodKey;

    public MvProductRankPublicationId(String periodType, String periodKey) {
        this.periodType = periodType;
        this.periodKey = periodKey;
    }
}
