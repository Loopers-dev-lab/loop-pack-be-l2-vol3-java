package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.LocalDate;

/**
 * carry-over 처리 이력. carry_over_date에 unique 제약을 두어 재실행을 차단한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "ranking_carry_over_history",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_ranking_carry_over_history_date",
        columnNames = {"carry_over_date"}
    )
)
public class RankingCarryOverHistory extends BaseEntity {

    @Column(name = "carry_over_date", nullable = false)
    private LocalDate carryOverDate;

    @Column(name = "carried_rows", nullable = false)
    private int carriedRows;

    public RankingCarryOverHistory(LocalDate carryOverDate, int carriedRows) {
        Assert.notNull(carryOverDate, "carryOverDate must not be null");
        Assert.state(carriedRows >= 0, "carriedRows must be >= 0");
        this.carryOverDate = carryOverDate;
        this.carriedRows = carriedRows;
    }
}
