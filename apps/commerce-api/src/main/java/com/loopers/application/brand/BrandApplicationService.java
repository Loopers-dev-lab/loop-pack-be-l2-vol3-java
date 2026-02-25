package com.loopers.application.brand;

import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.brand.command.UpdateBrandCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandApplicationService {

    private final BrandRepository brandRepository;

    @Transactional
    public Brand create(CreateBrandCommand command) {
        BrandName brandName = new BrandName(command.name());
        
        if (brandRepository.existsByName(brandName)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
        }

        Brand brand = new Brand(brandName, command.description(), command.imageUrl());

        try {
            return brandRepository.save(brand);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.");
        }
    }

    @Transactional(readOnly = true)
    public Brand findById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    @Transactional
    public Brand update(Long id, UpdateBrandCommand command) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        Brand updated = brand.update(command.description(), command.imageUrl());
        return brandRepository.save(updated);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));

        brandRepository.deleteRelatedProducts(brand.id());

        brandRepository.delete(brand);
    }
}
