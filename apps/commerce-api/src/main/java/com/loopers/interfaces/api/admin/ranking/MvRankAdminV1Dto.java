package com.loopers.interfaces.api.admin.ranking;

import com.loopers.application.ranking.MvRankStatusInfo;
import com.loopers.application.ranking.MvRankVersionCount;

import java.time.ZonedDateTime;
import java.util.List;

public class MvRankAdminV1Dto {

    public record StatusResponse(
            String periodType,
            String periodKey,
            long publishedVersion,
            long nextVersion,
            ZonedDateTime updatedAt,
            long totalRowCount,
            long publishedRowCount,
            long orphanRowCount,
            List<MvRankVersionCount> versionBreakdown
    ) {
        public static StatusResponse from(MvRankStatusInfo info) {
            return new StatusResponse(
                    info.periodType(), info.periodKey(),
                    info.publishedVersion(), info.nextVersion(),
                    info.updatedAt(), info.totalRowCount(), info.publishedRowCount(),
                    info.orphanRowCount(), info.versionBreakdown()
            );
        }
    }
}
