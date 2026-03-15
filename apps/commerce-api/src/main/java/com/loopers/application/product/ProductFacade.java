package com.loopers.application.product;

import com.loopers.application.product.dto.FindProductListReqDto;
import com.loopers.application.product.dto.FindProductListResDto;
import com.loopers.application.product.dto.FindProductResDto;
import com.loopers.domain.brand.service.BrandService;
import com.loopers.domain.favorite.service.FavoriteService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final MemberService memberService;
    private final FavoriteService favoriteService;

    public Page<FindProductListResDto> findProductList(FindProductListReqDto req, Pageable pageable) {
        if (req.brandId() != null) {
            brandService.findBrand(req.brandId());
        }
        Long memberId = resolveMemberId(req.loginId(), req.password());

        Page<ProductItem> products = productService.findProductList(req.brandId(), req.sortFilter(), pageable);
        List<Long> productIds = products.getContent().stream().map(ProductItem::id).toList();
        Set<Long> favoriteIds = favoriteService.getFavoriteProductIds(memberId, productIds);

        return products.map(item -> FindProductListResDto.from(
                item.withIsFavorite(favoriteIds.contains(item.id()))
        ));
    }

    public FindProductResDto findProduct(String loginId, String password, Long productId) {
        Long memberId = resolveMemberId(loginId, password);
        ProductItem item = productService.findProductDetail(productId);
        boolean isFavorite = memberId != null
                && favoriteService.existsByMemberIdAndProductId(memberId, productId);
        return FindProductResDto.from(item.withIsFavorite(isFavorite));
    }

    private Long resolveMemberId(String loginId, String password) {
        if (loginId == null || password == null) {
            return null;
        }
        Member member = memberService.findMember(loginId, password);
        return member.getId();
    }
}
