package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CompensationDlq;
import com.loopers.domain.payment.CompensationDlqRepository;
import com.loopers.domain.payment.CompensationDlqStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class CompensationDlqRepositoryImpl implements CompensationDlqRepository {

    private final CompensationDlqJpaRepository jpaRepository;
    private final CompensationDlqMapper mapper;

    public CompensationDlqRepositoryImpl(CompensationDlqJpaRepository jpaRepository,
                                          CompensationDlqMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public CompensationDlq save(CompensationDlq dlq) {
        CompensationDlqEntity entity = mapper.toEntity(dlq);
        CompensationDlqEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<CompensationDlq> findAllPending() {
        return jpaRepository.findAllByStatus(CompensationDlqStatus.PENDING)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
