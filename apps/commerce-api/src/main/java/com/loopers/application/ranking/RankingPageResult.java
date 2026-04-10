package com.loopers.application.ranking;

import java.util.List;

public record RankingPageResult(
        List<RankingProductInfo> items,
        int page,
        int size
) {}
