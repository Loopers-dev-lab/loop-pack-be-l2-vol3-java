package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CompensationDlqStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompensationDlqJpaRepository extends JpaRepository<CompensationDlqEntity, Long> {
    List<CompensationDlqEntity> findAllByStatus(CompensationDlqStatus status);
}
