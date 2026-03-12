package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandService {
    private final BrandRepository brandRepository;

    @Transactional
    public void register(String name) {
        if (brandRepository.findByName(name).isPresent()) {
            throw new CoreException(BrandErrorCode.DUPLICATE_NAME);
        }

        brandRepository.save(BrandModel.create(name));
    }

    @Transactional(readOnly = true)
    public BrandModel getById(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(BrandErrorCode.NOT_FOUND));
    }

    @Transactional
    public void update(Long id, String name) {
        BrandModel brandModel = getById(id);

        brandRepository.findByName(name)
            .filter(existing -> !existing.getId().equals(brandModel.getId()))
            .ifPresent(existing -> {
                throw new CoreException(BrandErrorCode.DUPLICATE_NAME);
            });

        brandModel.update(name);
    }

    @Transactional
    public void delete(Long id) {
        BrandModel brandModel = getById(id);
        brandModel.delete();
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getAll(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<BrandModel> getAllByIds(List<Long> ids) {
        return brandRepository.findAllByIdIn(ids);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getNameMapByIds(List<Long> ids) {
        return brandRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));
    }

    @Transactional(readOnly = true)
    public Map<Long, String> getActiveNameMapByIds(List<Long> ids) {
        return brandRepository.findAllByIdIn(ids).stream()
                .filter(brand -> brand.getDeletedAt() == null)
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));
    }

    @Transactional(readOnly = true)
    public void validateExists(Long id) {
        getById(id);
    }
}
