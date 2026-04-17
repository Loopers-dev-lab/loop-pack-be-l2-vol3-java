package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.ReconciliationMismatch;
import com.loopers.domain.payment.ReconciliationMismatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ReconciliationMismatchRepositoryImpl implements ReconciliationMismatchRepository {

    private final ReconciliationMismatchJpaRepository reconciliationMismatchJpaRepository;

    @Override
    public ReconciliationMismatch save(ReconciliationMismatch mismatch) {
        return reconciliationMismatchJpaRepository.save(mismatch);
    }

    @Override
    public Optional<ReconciliationMismatch> findById(Long id) {
        return reconciliationMismatchJpaRepository.findById(id);
    }

    @Override
    public List<ReconciliationMismatch> findAllByType(String type) {
        return reconciliationMismatchJpaRepository.findAllByTypeAndDeletedAtIsNull(type);
    }

    @Override
    public List<ReconciliationMismatch> findAllUnresolved() {
        return reconciliationMismatchJpaRepository.findAllByResolvedAtIsNullAndDeletedAtIsNull();
    }
}
