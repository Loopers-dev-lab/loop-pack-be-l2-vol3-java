package com.loopers.application.brand;

import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.brand.BrandName;
import com.loopers.domain.model.common.DomainEventPublisher;
import com.loopers.domain.repository.BrandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BrandService implements CreateBrandUseCase, UpdateBrandUseCase, DeleteBrandUseCase {

    private final BrandRepository brandRepository;
    private final DomainEventPublisher eventPublisher;

    public BrandService(BrandRepository brandRepository, DomainEventPublisher eventPublisher) {
        this.brandRepository = brandRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void createBrand(String name, String description) {
        BrandName brandName = BrandName.of(name);
        if (brandRepository.existsByName(brandName)) {
            throw new IllegalArgumentException("이미 존재하는 브랜드 이름입니다.");
        }
        Brand brand = Brand.create(brandName, description);
        brandRepository.save(brand);
    }

    @Override
    public void updateBrand(Long brandId, String name, String description) {
        Brand brand = findBrand(brandId);
        Brand updated = brand.update(BrandName.of(name), description);
        brandRepository.save(updated);
    }

    @Override
    public void deleteBrand(Long brandId) {
        Brand brand = findBrand(brandId);
        Brand deleted = brand.delete();
        brandRepository.save(deleted);
        eventPublisher.publishEvents(deleted);
    }

    private Brand findBrand(Long brandId) {
        return brandRepository.findActiveById(brandId)
                .orElseThrow(() -> new IllegalArgumentException("브랜드를 찾을 수 없습니다."));
    }
}
