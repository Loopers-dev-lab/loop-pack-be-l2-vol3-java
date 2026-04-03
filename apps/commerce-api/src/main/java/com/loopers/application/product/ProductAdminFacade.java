package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCacheEvictEvent;
import com.loopers.domain.product.ProductDetailCacheEvictEvent;
import com.loopers.domain.product.ProductEventPublisher;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductAdminFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductEventPublisher eventPublisher;

    // 상품 등록 - 브랜드 존재 확인은 Facade 책임 (BR-P01, US-P05)
    @Transactional
    public ProductInfo register(ProductRegisterCommand command) {
        Brand brand = brandService.findById(command.brandId()); // 브랜드 미존재 시 NOT_FOUND 예외
        Product product = productService.register(
                command.brandId(), command.name(), command.price(), command.stock());
        eventPublisher.publish(new ProductCacheEvictEvent()); // 커밋 후 캐시 무효화 예약
        return ProductInfo.from(product, brand.getName());
    }

    // 상품 상세 조회
    @Transactional(readOnly = true)
    public ProductInfo findById(Long id) {
        Product product = productService.findById(id);
        // 상품의 brandId로 브랜드명 조회
        String brandName = brandService.findById(product.getBrandId()).getName();
        return ProductInfo.from(product, brandName);
    }

    // 상품 목록 조회 (brandId 필터 선택)
    @Transactional(readOnly = true)
    public Page<ProductInfo> findAll(Long brandId, Pageable pageable) {
        Page<Product> products = productService.findAll(brandId, pageable);
        // 상품들의 brandId만 추출
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();
        // brandId에 해당하는 브랜드명을 조회하면 Map으로 변환
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);
        // 기존의 상품 List 각각의 product에 해당하는 브랜드명 매핑
        return products.map(product -> ProductInfo.from(product, brandNameMap.get(product.getBrandId())));
    }

    // 상품 정보 수정
    @Transactional
    public ProductInfo update(ProductUpdateCommand command) {
        Product product = productService.update(
                command.id(), command.name(), command.price(), command.stock());
        eventPublisher.publish(new ProductCacheEvictEvent());                        // 목록 캐시 전체 무효화
        eventPublisher.publish(new ProductDetailCacheEvictEvent(product.getId()));   // 상세 캐시 핀포인트 무효화
        String brandName = brandService.findById(product.getBrandId()).getName();
        return ProductInfo.from(product, brandName);
    }

    /**
     * 상품 삭제 (US-P07)
     * 좋아요(hard delete) → 상품(soft delete) → 커밋 후 캐시 무효화 순서로 처리
     */
    @Transactional
    public void delete(Long id) {
        // 상품 존재 확인 + 참조 확보 (없으면 NOT_FOUND 예외)
        Product product = productService.findById(id);
        // 좋아요 cascade hard delete
        likeService.deleteAllByProductId(id);
        // 상품 soft delete (이미 managed 상태이므로 dirty checking으로 처리)
        product.delete();
        eventPublisher.publish(new ProductCacheEvictEvent());             // 목록 캐시 전체 무효화
        eventPublisher.publish(new ProductDetailCacheEvictEvent(id));     // 상세 캐시 핀포인트 무효화
    }
}
