package com.loopers.application.coupon.category;

import com.loopers.application.coupon.category.CategoryCacheRepository;
import com.loopers.domain.category.Category;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryApplicationService {
    private final CategoryCacheRepository categoryCacheRepository;

    public Category findById(UUID categoryId) {
        return categoryCacheRepository.findById(categoryId)
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "카테고리를 찾을 수 없습니다."));
    }

    public Page<Category> list(Pageable pageable) {
        List<Category> categories = categoryCacheRepository.findAll();
        if (pageable.isUnpaged()) {
            return new PageImpl<>(categories, pageable, categories.size());
        }
        int start = Math.min((int) pageable.getOffset(), categories.size());
        int end = Math.min(start + pageable.getPageSize(), categories.size());
        return new PageImpl<>(categories.subList(start, end), pageable, categories.size());
    }
}
