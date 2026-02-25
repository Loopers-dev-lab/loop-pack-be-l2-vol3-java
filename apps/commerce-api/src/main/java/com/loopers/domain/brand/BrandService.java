package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public BrandModel getBrand(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    @Transactional(readOnly = true)
    public Page<BrandModel> getAll(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional
    public BrandModel register(String name, String description) {
        brandRepository.findByName(name)
            .ifPresent(brand -> {
                throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
            });

        BrandModel brand = new BrandModel(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public BrandModel update(Long id, String name, String description) {
        BrandModel brand = brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

        brand.update(name, description);
        return brand;
    }

    @Transactional
    public void delete(Long id) {
        BrandModel brand = brandRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

        brand.delete();
    }
}
