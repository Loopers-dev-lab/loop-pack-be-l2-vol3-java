package com.loopers.interfaces.api.ranking;

import com.loopers.domain.ranking.WeightConfig;

public class WeightConfigV1Dto {

    // Command

    public record CreateRequest(
            String groupName,
            double wView,
            double wLike,
            double wOrder,
            int trafficPct
    ) {
    }

    public record UpdateRequest(
            double wView,
            double wLike,
            double wOrder,
            int trafficPct
    ) {
    }

    // Response

    public record Response(
            Long id,
            String groupName,
            double wView,
            double wLike,
            double wOrder,
            int trafficPct,
            boolean active
    ) {
        public static Response from(WeightConfig config) {
            return new Response(
                    config.getId(),
                    config.getGroupName(),
                    config.getWView(),
                    config.getWLike(),
                    config.getWOrder(),
                    config.getTrafficPct(),
                    config.isActive()
            );
        }
    }
}
