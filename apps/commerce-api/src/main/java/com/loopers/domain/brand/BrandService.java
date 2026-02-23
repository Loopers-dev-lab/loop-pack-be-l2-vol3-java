package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandService {
    private final BrandRepository brandRepository;

    // 브랜드 등록
    @Transactional
    public Brand register(String name) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        Brand brand = new Brand(name);
        return brandRepository.save(brand);
    }

    // 브랜드 상세 조회
    @Transactional(readOnly = true)
    public Brand findById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    // 브랜드 목록 조회
    @Transactional(readOnly = true)
    public Page<Brand> findAll(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    // 브랜드 정보 수정
    @Transactional
    public Brand update(Long id, String name) {
        Brand brand = findById(id);
        if (brandRepository.existsByNameAndIdNot(name, id)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }
        // DB에서 brand 정보를 불러왔기 때문에, 이미 영속 상태이므로 따로 save()를 호출하지 않아도 된다.
        brand.update(name);
        return brand;
    }

    // 브랜드 삭제
    @Transactional
    public void delete(Long id) {
        Brand brand = findById(id);
        brand.delete();
    }
}
