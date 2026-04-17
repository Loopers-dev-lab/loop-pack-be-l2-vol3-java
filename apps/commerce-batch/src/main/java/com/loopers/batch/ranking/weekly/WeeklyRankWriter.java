package com.loopers.batch.ranking.weekly;

import com.loopers.domain.rank.MvProductRankWeekly;
import com.loopers.domain.rank.MvProductRankWeeklyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class WeeklyRankWriter implements ItemWriter<MvProductRankWeekly> {

    private final MvProductRankWeeklyRepository repository;

    @Override
    public void write(Chunk<? extends MvProductRankWeekly> chunk) {
        repository.upsertAll(chunk.getItems());
    }
}
