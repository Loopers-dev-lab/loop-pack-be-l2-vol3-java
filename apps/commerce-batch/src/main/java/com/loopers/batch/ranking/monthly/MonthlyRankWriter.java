package com.loopers.batch.ranking.monthly;

import com.loopers.domain.rank.MvProductRankMonthly;
import com.loopers.domain.rank.MvProductRankMonthlyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class MonthlyRankWriter implements ItemWriter<MvProductRankMonthly> {

    private final MvProductRankMonthlyRepository repository;

    @Override
    public void write(Chunk<? extends MvProductRankMonthly> chunk) {
        repository.upsertAll(chunk.getItems());
    }
}
