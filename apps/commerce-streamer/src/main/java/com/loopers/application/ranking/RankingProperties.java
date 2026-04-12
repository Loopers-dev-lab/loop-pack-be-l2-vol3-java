package com.loopers.application.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("loopers.ranking")
public record RankingProperties(
        @DefaultValue Weight weight,
        @DefaultValue CarryOver carryOver,
        @DefaultValue Sync sync
) {
    public record Weight(
            @DefaultValue("0.1") double view,
            @DefaultValue("0.2") double like,
            @DefaultValue("0.7") double sales
    ) {
    }

    public record CarryOver(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0.1") double ratio,
            @DefaultValue("0 50 23 * * *") String dailyCron
    ) {
    }

    public record Sync(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("60000") long fixedDelayMs,
            @DefaultValue("true") boolean immediateIncrementEnabled
    ) {
    }
}
