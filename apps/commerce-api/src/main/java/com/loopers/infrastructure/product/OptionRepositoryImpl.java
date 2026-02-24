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
        if (option.getId() == null) {
            OptionJpaEntity entity = OptionJpaEntity.from(option);
            OptionJpaEntity saved = optionJpaRepository.save(entity);
            return saved.toDomain();
        }

        OptionJpaEntity entity = optionJpaRepository.findById(option.getId())
                .orElseThrow(() -> new IllegalStateException("Option not found: " + option.getId()));
        entity.update(option);
        return entity.toDomain();
    }

    @Override
    public Optional<Option> findById(Long id) {
        return optionJpaRepository.findByIdAndDeletedFalse(id)
                .map(OptionJpaEntity::toDomain);
    }

    @Override
    public List<Option> findByProductId(Long productId) {
        return optionJpaRepository.findByProductIdAndDeletedFalse(productId).stream()
                .map(OptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Option> findByProductIdIn(List<Long> productIds) {
        return optionJpaRepository.findByProductIdInAndDeletedFalse(productIds).stream()
                .map(OptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Option> findByIdIn(List<Long> optionIds) {
        return optionJpaRepository.findByIdInAndDeletedFalse(optionIds).stream()
                .map(OptionJpaEntity::toDomain)
                .toList();
    }
}
