package com.loopers.domain.ranking;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class FakeRankingCarryOverHistoryRepository implements RankingCarryOverHistoryRepository {

    private final Set<LocalDate> dates = new HashSet<>();
    private boolean throwOnSave = false;

    public void setThrowOnSave(boolean v) {
        this.throwOnSave = v;
    }

    @Override
    public boolean existsByCarryOverDate(LocalDate date) {
        return dates.contains(date);
    }

    @Override
    public RankingCarryOverHistory save(RankingCarryOverHistory history) {
        if (throwOnSave) {
            throw new DataIntegrityViolationException("simulated unique key violation");
        }
        if (!dates.add(history.getCarryOverDate())) {
            throw new DataIntegrityViolationException("duplicate carry_over_date");
        }
        return history;
    }
}
