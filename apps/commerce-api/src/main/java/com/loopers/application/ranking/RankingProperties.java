package com.loopers.application.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
    Experiment experiment
) {
    public RankingProperties {
        if (experiment == null) experiment = new Experiment(false, Map.of());
    }

    public record Experiment(boolean enabled, Map<String, Variant> variants) {
        public Experiment {
            if (variants == null) variants = Map.of();
        }
    }

    public record Variant(String zsetPrefix) {}
}
