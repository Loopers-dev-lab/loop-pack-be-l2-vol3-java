package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class RankingFlushBatchService {

    private final RankingDeltaPendingRepository rankingDeltaPendingRepository;
    private final RankingRepository rankingRepository;

    @Transactional
    public void flushProcessedEvents(List<String> processedEventIds) {
        if (processedEventIds.isEmpty()) {
            return;
        }

        List<RankingDeltaPending> pendingDeltas = rankingDeltaPendingRepository.findPendingByEventIds(processedEventIds);
        if (pendingDeltas.isEmpty()) {
            return;
        }

        Map<LocalDate, Map<Long, Double>> deltaByDateAndProduct = new HashMap<>();
        List<Long> flushedIds = new ArrayList<>();

        for (RankingDeltaPending delta : pendingDeltas) {
            deltaByDateAndProduct
                .computeIfAbsent(delta.getRankingDate(), ignored -> new HashMap<>())
                .merge(delta.getProductId(), delta.getDelta(), Double::sum);
            flushedIds.add(delta.getId());
        }

        rankingRepository.flush(deltaByDateAndProduct);
        rankingDeltaPendingRepository.markAsFlushed(flushedIds);
    }
}
