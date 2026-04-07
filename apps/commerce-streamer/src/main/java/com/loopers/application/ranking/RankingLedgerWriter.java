package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.domain.ranking.RankingScoreLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 단일 productId 단위 ledger upsert. REQUIRES_NEW로 새 트랜잭션을 강제한다.
 *
 * <p>구현 전략: 원자적 UPDATE(addDelta) 우선, 없으면 INSERT, INSERT race 시 addDelta 재시도.
 * 이로써 동일 row 동시 update에서 발생하는 lost update를 DB 수준에서 봉쇄한다.
 *
 * <p>같은 클래스 내 self-invocation은 Spring proxy가 적용되지 않으므로 별도 빈으로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class RankingLedgerWriter {

    private final RankingScoreLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertSingle(
        RankingScoreLedger.BucketType bucketType, String bucketKey, Long productId, double delta
    ) {
        Instant now = Instant.now();

        // 1. 원자적 UPDATE 시도 — 존재하는 row면 단일 쿼리로 완료, race free
        int updated = ledgerRepository.addDelta(bucketType, bucketKey, productId, delta, now);
        if (updated > 0) {
            return;
        }

        // 2. row 없음 → INSERT 시도
        try {
            RankingScoreLedger fresh = new RankingScoreLedger(bucketType, bucketKey, productId);
            fresh.addScore(delta, now);
            ledgerRepository.save(fresh);
        } catch (DataIntegrityViolationException race) {
            // 3. INSERT race (다른 스레드가 먼저 INSERT) → addDelta 재시도
            int retried = ledgerRepository.addDelta(bucketType, bucketKey, productId, delta, now);
            if (retried == 0) {
                throw race;
            }
        }
    }
}
