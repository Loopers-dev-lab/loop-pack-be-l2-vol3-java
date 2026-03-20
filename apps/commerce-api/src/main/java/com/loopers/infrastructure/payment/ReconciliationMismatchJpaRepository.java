package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.ReconciliationMismatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationMismatchJpaRepository extends JpaRepository<ReconciliationMismatch, Long> {

    List<ReconciliationMismatch> findAllByTypeAndDeletedAtIsNull(String type);

    List<ReconciliationMismatch> findAllByResolvedAtIsNullAndDeletedAtIsNull();
}
