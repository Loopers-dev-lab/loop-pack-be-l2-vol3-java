package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface ReconciliationMismatchRepository {
    ReconciliationMismatch save(ReconciliationMismatch mismatch);
    Optional<ReconciliationMismatch> findById(Long id);
    List<ReconciliationMismatch> findAllByType(String type);
    List<ReconciliationMismatch> findAllUnresolved();
}
