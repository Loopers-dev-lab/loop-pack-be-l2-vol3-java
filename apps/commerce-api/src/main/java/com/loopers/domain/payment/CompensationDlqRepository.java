package com.loopers.domain.payment;

import java.util.List;

public interface CompensationDlqRepository {
    CompensationDlq save(CompensationDlq dlq);
    List<CompensationDlq> findAllPending();
}
