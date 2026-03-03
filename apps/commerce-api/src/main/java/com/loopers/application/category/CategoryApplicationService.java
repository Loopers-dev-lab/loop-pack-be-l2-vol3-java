package com.loopers.application.category;

import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryApplicationService {
    private final CategoryRepository categoryRepository;

    public Category findById(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "카테고리를 찾을 수 없습니다."));
    }

    public Page<Category> list(Pageable pageable) {
        return categoryRepository.findAll(pageable);
    }
}
