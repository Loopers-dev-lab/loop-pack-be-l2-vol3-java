package com.loopers.domain.ranking.model;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Locale;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static RankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return RankingPeriod.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 랭킹 기간입니다.");
        }
    }
}
