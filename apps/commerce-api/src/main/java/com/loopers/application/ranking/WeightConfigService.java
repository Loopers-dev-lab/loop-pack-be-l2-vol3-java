package com.loopers.application.ranking;

import com.loopers.domain.ranking.WeightConfig;
import com.loopers.domain.ranking.WeightConfigRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WeightConfigService {

    private final WeightConfigRepository weightConfigRepository;

    // Query

    @Transactional(readOnly = true)
    public List<WeightConfig> getAllActive() {
        return weightConfigRepository.findAllByActiveTrue();
    }

    // Command

    @Transactional
    public WeightConfig create(String groupName, double wView, double wLike, double wOrder, int trafficPct) {
        weightConfigRepository.findByGroupName(groupName).ifPresent(existing -> {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 그룹입니다. groupName=" + groupName);
        });
        WeightConfig config = WeightConfig.create(groupName, wView, wLike, wOrder, trafficPct);
        return weightConfigRepository.save(config);
    }

    @Transactional
    public WeightConfig update(String groupName, double wView, double wLike, double wOrder, int trafficPct) {
        WeightConfig config = weightConfigRepository.findByGroupName(groupName)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 그룹입니다. groupName=" + groupName));
        config.updateWeights(wView, wLike, wOrder, trafficPct);
        return config;
    }

    @Transactional
    public void deactivate(String groupName) {
        if ("control".equals(groupName)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "control 그룹은 비활성화할 수 없습니다");
        }
        WeightConfig config = weightConfigRepository.findByGroupName(groupName)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 그룹입니다. groupName=" + groupName));
        config.deactivate();
    }
}
