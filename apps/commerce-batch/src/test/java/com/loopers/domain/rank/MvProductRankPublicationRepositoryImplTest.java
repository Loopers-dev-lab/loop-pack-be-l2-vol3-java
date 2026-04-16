package com.loopers.domain.rank;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("Publication 레포지토리 — bump/find/CAS")
class MvProductRankPublicationRepositoryImplTest {

    private static final String KEY = "2026W15";

    @Autowired MvProductRankPublicationRepository repository;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("bumpNextVersion 최초 호출 시 row 생성 + version=1")
    @Test
    void bumpFirst_createsRow() {
        long v = repository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        assertThat(v).isEqualTo(1L);
        assertThat(repository.findPublishedVersion(RankPeriodType.WEEKLY, KEY)).isZero();
    }

    @DisplayName("bumpNextVersion 반복 호출 시 version 단조 증가")
    @Test
    void bumpRepeat_monotonic() {
        long v1 = repository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        long v2 = repository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        long v3 = repository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        assertThat(v1).isEqualTo(1L);
        assertThat(v2).isEqualTo(2L);
        assertThat(v3).isEqualTo(3L);
    }

    @DisplayName("CAS publish — 더 큰 version일 때만 반영")
    @Test
    void casPublish_onlyIfGreater() {
        repository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        repository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        boolean applied = repository.casPublishIfGreater(RankPeriodType.WEEKLY, KEY, 2L);
        assertThat(applied).isTrue();
        assertThat(repository.findPublishedVersion(RankPeriodType.WEEKLY, KEY)).isEqualTo(2L);

        boolean staleApplied = repository.casPublishIfGreater(RankPeriodType.WEEKLY, KEY, 1L);
        assertThat(staleApplied).as("현 published(2) 보다 작은 값은 무시").isFalse();
        assertThat(repository.findPublishedVersion(RankPeriodType.WEEKLY, KEY)).isEqualTo(2L);

        boolean laterApplied = repository.casPublishIfGreater(RankPeriodType.WEEKLY, KEY, 3L);
        assertThat(laterApplied).isTrue();
        assertThat(repository.findPublishedVersion(RankPeriodType.WEEKLY, KEY)).isEqualTo(3L);
    }

    @DisplayName("periodType별 독립 — WEEKLY와 MONTHLY는 각자의 version 관리")
    @Test
    void perPeriodType_isolated() {
        repository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        long monthlyV = repository.bumpNextVersion(RankPeriodType.MONTHLY, KEY);
        assertThat(monthlyV).isEqualTo(1L);
    }
}
