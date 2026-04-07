package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingScoreLedger;
import com.loopers.domain.ranking.RankingScoreLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 단일 productId 단위 ledger upsert. REQUIRES_NEW로 새 트랜잭션을 강제한다.
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
        RankingScoreLedger ledger = ledgerRepository.findByBucket(bucketType, bucketKey, productId)
            .orElseGet(() -> new RankingScoreLedger(bucketType, bucketKey, productId));
        ledger.addScore(delta);
        ledgerRepository.save(ledger);
    }
}
