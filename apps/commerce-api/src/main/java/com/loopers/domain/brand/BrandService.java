package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandModel register(String name, String description, String imageUrl) {
        return brandRepository.save(new BrandModel(name, description, imageUrl));
    }

    @Transactional(readOnly = true)
    public BrandModel getById(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    @Transactional(readOnly = true)
    public Map<Long, BrandModel> getByIds(Set<Long> ids) {
        return brandRepository.findAllByIds(ids).stream()
                .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }
}
