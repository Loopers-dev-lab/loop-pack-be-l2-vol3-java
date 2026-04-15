package com.loopers.domain.rank;

public interface MvProductRankPublicationRepository {

    long bumpNextVersion(RankPeriodType type, String periodKey);

    long findPublishedVersion(RankPeriodType type, String periodKey);

    boolean casPublishIfGreater(RankPeriodType type, String periodKey, long newVersion);
}
