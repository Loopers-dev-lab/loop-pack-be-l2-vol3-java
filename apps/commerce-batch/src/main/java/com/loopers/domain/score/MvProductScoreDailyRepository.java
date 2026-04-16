package com.loopers.domain.score;

import java.util.List;

public interface MvProductScoreDailyRepository {

    void batchUpsert(List<MvProductScoreDailyRow> rows);
}
