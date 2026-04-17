package com.loopers.domain.ranking;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RankingVersionManager {

    private final AtomicLong weeklyVersion = new AtomicLong(0);
    private final AtomicLong monthlyVersion = new AtomicLong(0);

    public long getNextWeeklyVersion() {
        return weeklyVersion.get() + 1;
    }

    public long getCurrentWeeklyVersion() {
        return weeklyVersion.get();
    }

    public void activateWeeklyVersion(long version) {
        weeklyVersion.set(version);
    }

    public long getNextMonthlyVersion() {
        return monthlyVersion.get() + 1;
    }

    public long getCurrentMonthlyVersion() {
        return monthlyVersion.get();
    }

    public void activateMonthlyVersion(long version) {
        monthlyVersion.set(version);
    }
}
