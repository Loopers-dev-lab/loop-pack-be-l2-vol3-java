package com.loopers.domain.score;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class MvProductScoreDailyId implements Serializable {

    @Column(name = "product_db_id", nullable = false)
    private Long productDbId;

    @Column(name = "score_date", nullable = false)
    private LocalDate scoreDate;

    public MvProductScoreDailyId(Long productDbId, LocalDate scoreDate) {
        this.productDbId = productDbId;
        this.scoreDate = scoreDate;
    }
}
