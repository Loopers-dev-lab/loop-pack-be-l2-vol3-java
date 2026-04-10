package com.loopers.application.ranking;

import com.loopers.domain.ranking.WeightConfig;
import com.loopers.domain.ranking.WeightConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExperimentGroupResolver {

    private final WeightConfigRepository weightConfigRepository;

    public String resolve(Long userId) {
        if (userId == null) return "control";

        List<WeightConfig> configs = weightConfigRepository.findAllByActiveTrue();
        if (configs.size() <= 1) return "control";

        int bucket = Math.abs(userId.hashCode() % 100);

        int cumulative = 0;
        for (WeightConfig config : configs) {
            if ("control".equals(config.getGroupName())) continue;
            cumulative += config.getTrafficPct();
            if (bucket < cumulative) {
                return config.getGroupName();
            }
        }
        return "control";
    }
}
