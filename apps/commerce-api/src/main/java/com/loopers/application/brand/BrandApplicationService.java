package com.loopers.application.brand;

import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.brand.command.UpdateBrandCommand;
import com.loopers.application.brand.BrandCacheRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.infrastructure.brand.redis.BrandCacheSyncer;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandApplicationService {

    private final BrandRepository brandRepository;
    private final BrandCacheRepository brandCacheRepository;
    private final BrandCacheSyncer brandCacheSyncer;

    @Transactional
    public Brand create(CreateBrandCommand command) {
        BrandName brandName = new BrandName(command.name());
        
        if (brandRepository.existsByName(brandName)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
        }

        Brand brand = new Brand(brandName, command.description(), command.imageUrl());

        try {
            Brand saved = brandRepository.save(brand);
            brandCacheSyncer.registerUpsert(saved);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
        }
    }

    @Transactional(readOnly = true)
    public Brand findById(UUID id) {
        return brandCacheRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<Brand> list(Pageable pageable) {
        List<Brand> brands = brandCacheRepository.findAll();
        if (pageable.isUnpaged()) {
            return new PageImpl<>(brands, pageable, brands.size());
        }
        int start = Math.min((int) pageable.getOffset(), brands.size());
        int end = Math.min(start + pageable.getPageSize(), brands.size());
        return new PageImpl<>(brands.subList(start, end), pageable, brands.size());
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> findNamesByIds(Collection<UUID> brandIds) {
        return brandCacheRepository.findNamesByIds(brandIds);
    }

    @Transactional
    public Brand update(UUID id, UpdateBrandCommand command) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        Brand updated = brand.update(command.description(), command.imageUrl());
        Brand saved = brandRepository.save(updated);
        brandCacheSyncer.registerUpsert(saved);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        brandRepository.delete(brand);
        brandCacheSyncer.registerDelete(id);
    }
}
