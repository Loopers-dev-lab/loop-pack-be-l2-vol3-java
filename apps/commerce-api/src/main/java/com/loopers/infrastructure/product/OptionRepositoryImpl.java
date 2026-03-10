package com.loopers.infrastructure.product;

import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OptionRepositoryImpl implements OptionRepository {
    private final OptionJpaRepository optionJpaRepository;

    @Override
    public Option save(Option option) {
        return optionJpaRepository.save(option);
    }

    @Override
    public Optional<Option> findById(Long id) {
        return optionJpaRepository.findByIdAndDeletedFalse(id);
    }

    @Override
    public List<Option> findByProductId(Long productId) {
        return optionJpaRepository.findByProductIdAndDeletedFalse(productId);
    }

    @Override
    public List<Option> findByProductIdIn(List<Long> productIds) {
        return optionJpaRepository.findByProductIdInAndDeletedFalse(productIds);
    }

    @Override
    public List<Option> findByIdIn(List<Long> optionIds) {
        return optionJpaRepository.findByIdInAndDeletedFalse(optionIds);
    }

    @Override
    public Optional<Option> findByIdWithLock(Long id) {
        return optionJpaRepository.findByIdWithLock(id);
    }
}
