package com.loopers.application.ranking;

import java.util.List;

public record RankingCursorResult(
        List<RankingInfo> items,
        Double nextCursor
) {
}
