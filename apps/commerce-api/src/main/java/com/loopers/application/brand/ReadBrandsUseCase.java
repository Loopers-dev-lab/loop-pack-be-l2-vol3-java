package com.loopers.application.brand;

import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 브랜드 목록을 조회합니다.
 *
 * <p>삭제된 브랜드를 포함한 전체 목록을 최신 등록순으로 페이징하여 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadBrandsUseCase {

    private final BrandRepository brandRepository;

    /**
     * @param pageSize 페이지 크기
     * @return 브랜드 목록 페이지 (최신순)
     */
    @Transactional(readOnly = true)
    public Page<BrandResult> execute(PageSize pageSize) {
        Slice<Brand> brands = brandRepository.findAllBy(
                pageSize.toPageable(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new Page<>(brands.getContent(), brands.hasNext())
                .map(BrandResult::from);
    }
}
