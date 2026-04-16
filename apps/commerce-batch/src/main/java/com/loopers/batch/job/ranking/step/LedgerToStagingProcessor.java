package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.dto.DailyLedgerRow;
import com.loopers.batch.job.ranking.dto.StagingDelta;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class LedgerToStagingProcessor implements ItemProcessor<DailyLedgerRow, StagingDelta> {

    @Override
    public StagingDelta process(DailyLedgerRow item) {
        return new StagingDelta(item.productId(), item.basePoints());
    }
}
