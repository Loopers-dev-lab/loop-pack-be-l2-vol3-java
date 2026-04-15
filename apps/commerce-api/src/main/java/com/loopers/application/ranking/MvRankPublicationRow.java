package com.loopers.application.ranking;

import java.sql.Timestamp;

record MvRankPublicationRow(
        String periodType,
        String periodKey,
        long publishedVersion,
        long nextVersion,
        Timestamp updatedAt
) {
}
