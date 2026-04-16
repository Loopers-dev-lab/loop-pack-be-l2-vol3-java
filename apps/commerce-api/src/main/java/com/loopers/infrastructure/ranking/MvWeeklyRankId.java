package com.loopers.infrastructure.ranking;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MvWeeklyRankId implements Serializable {

    private LocalDate baseDate;
    private Long productId;
}
