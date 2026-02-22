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
        return optionJpaRepository.findById(id)
                .map(OptionJpaEntity::toDomain);
    }

    @Override
    public List<Option> findByProductId(Long productId) {
        return optionJpaRepository.findByProductId(productId).stream()
                .map(OptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteByProductId(Long productId) {
        optionJpaRepository.deleteByProductId(productId);
    }
}
