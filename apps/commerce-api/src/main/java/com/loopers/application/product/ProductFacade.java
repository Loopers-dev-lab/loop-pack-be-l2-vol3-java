package com.loopers.application.product;

import com.loopers.application.product.dto.FindProductListResDto;
import com.loopers.application.product.dto.FindProductResDto;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandService;
import com.loopers.domain.favorite.service.FavoriteService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.service.ProductService;
import com.loopers.support.enums.SortFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final FavoriteService favoriteService;
    private final MemberService memberService;

    public Page<FindProductListResDto> findProductList(String loginId, String password, Long brandId, SortFilter sortFilter, Pageable pageable) {
        if (brandId != null) {
            brandService.findBrand(brandId);
        }
        Long memberId = resolveMemberId(loginId, password);
        Page<ProductItem> items = productService.findProductList(brandId, memberId, sortFilter, pageable);
        return items.map(FindProductListResDto::from);
    }

    public FindProductResDto findProduct(String loginId, String password, Long productId) {
        Product product = productService.findProduct(productId);
        Brand brand = brandService.findBrand(product.getBrandId());

        long favoriteCnt = favoriteService.countByProductId(productId);
        Long memberId = resolveMemberId(loginId, password);
        boolean isFavorite = memberId != null && favoriteService.existsByMemberIdAndProductId(memberId, productId);

        return FindProductResDto.of(product, brand, favoriteCnt, isFavorite);
    }

    private Long resolveMemberId(String loginId, String password) {
        if (loginId == null || password == null) {
            return null;
        }
        Member member = memberService.findMember(loginId, password);
        return member.getId();
    }
}
