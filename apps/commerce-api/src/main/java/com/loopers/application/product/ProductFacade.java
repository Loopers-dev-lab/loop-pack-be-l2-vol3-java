package com.loopers.application.product;

import com.loopers.application.product.dto.ProductCriteria;
import com.loopers.application.product.dto.ProductResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ImageType;
import com.loopers.domain.product.ProductImageService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.cache.CacheType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductImageService productImageService;

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_PRICE, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LIKES, allEntries = true)})
    @Transactional
    public void registerProduct(ProductCriteria.Register criteria) {
        brandService.validateExists(criteria.brandId());
        productService.register(criteria.brandId(), criteria.name(), criteria.price(), criteria.stock());
    }

    @Transactional(readOnly = true)
    public ProductResult getProduct(Long id) {
        ProductModel product = productService.getById(id);
        return ProductResult.of(
                product,
                brandService.getById(product.getBrandId()).getName());
    }

    @Transactional(readOnly = true)
    public ProductResult.DetailWithImages getProductDetail(Long id) {
        ProductModel product = productService.getById(id);
        return new ProductResult.DetailWithImages(
                ProductResult.of(
                        product,
                        brandService.getById(product.getBrandId()).getName()),
                productImageService.getImagesByProductIdAndType(id, ImageType.MAIN).stream()
                        .map(ProductResult.ImageResult::from)
                        .toList(),
                productImageService.getImagesByProductIdAndType(id, ImageType.DETAIL).stream()
                        .map(ProductResult.ImageResult::from)
                        .toList());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_PRICE, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LIKES, allEntries = true)})
    @Transactional
    public void updateProduct(Long id, ProductCriteria.Update criteria) {
        productService.update(id, criteria.name(), criteria.price(), criteria.stock());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_PRICE, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LIKES, allEntries = true)})
    @Transactional
    public void deleteProduct(Long id) {
        productService.delete(id);
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProducts(Pageable pageable) {
        Page<ProductModel> products = productService.getAll(pageable);
        Map<Long, String> brandNameMap = brandService.getNameMapByIds(
                ProductModel.extractDistinctBrandIds(products.getContent()));
        return products.map(product -> ProductResult.of(product, brandNameMap.get(product.getBrandId())));
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProductsByBrandId(Long brandId, Pageable pageable) {
        String brandName = brandService.getById(brandId).getName();
        return productService.getAllByBrandId(brandId, pageable)
                .map(product -> ProductResult.of(product, brandName));
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProductsWithActiveBrand(Pageable pageable) {
        Page<ProductModel> products = productService.getAll(pageable);
        return new PageImpl<>(
                ProductResult.fromWithActiveBrand(
                        products.getContent(),
                        brandService.getActiveNameMapByIds(
                                ProductModel.extractDistinctBrandIds(products.getContent()))),
                products.getPageable(),
                products.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProductsWithActiveBrandByBrandId(Long brandId, Pageable pageable) {
        Page<ProductModel> products = productService.getAllByBrandId(brandId, pageable);
        return new PageImpl<>(
                ProductResult.fromWithActiveBrand(
                        products.getContent(),
                        brandService.getActiveNameMapByIds(
                                ProductModel.extractDistinctBrandIds(products.getContent()))),
                products.getPageable(),
                products.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProductsWithActiveBrandSortedByLikes(int page, int size) {
        Page<ProductModel> products = productService.getAllSortedByLikeCountDesc(
                PageRequest.of(page, size));
        return new PageImpl<>(
                ProductResult.fromWithActiveBrand(
                        products.getContent(),
                        brandService.getActiveNameMapByIds(
                                ProductModel.extractDistinctBrandIds(products.getContent()))),
                products.getPageable(),
                products.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<ProductResult> getProductsWithActiveBrandByBrandIdSortedByLikes(
            Long brandId, int page, int size) {
        Page<ProductModel> products = productService.getAllByBrandIdSortedByLikeCountDesc(
                brandId, PageRequest.of(page, size));
        return new PageImpl<>(
                ProductResult.fromWithActiveBrand(
                        products.getContent(),
                        brandService.getActiveNameMapByIds(
                                ProductModel.extractDistinctBrandIds(products.getContent()))),
                products.getPageable(),
                products.getTotalElements());
    }

    @Cacheable(cacheNames = CacheType.Names.PRODUCT_LIST_LATEST,
            key = "(#brandId ?: 'all') + ':' + #page + ':' + #size")
    @Transactional(readOnly = true)
    public ProductResult.ListPage getProductListLatest(Long brandId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ProductResult.ListPage.from(brandId != null
                ? getProductsWithActiveBrandByBrandId(brandId, pageable)
                : getProductsWithActiveBrand(pageable));
    }

    @Cacheable(cacheNames = CacheType.Names.PRODUCT_LIST_PRICE,
            key = "#sort + ':' + (#brandId ?: 'all') + ':' + #page + ':' + #size")
    @Transactional(readOnly = true)
    public ProductResult.ListPage getProductListByPrice(
            Long brandId, String sort, int page, int size) {
        Sort sortOrder = "price_asc".equals(sort)
                ? Sort.by(Sort.Direction.ASC, "price")
                : Sort.by(Sort.Direction.DESC, "price");
        return ProductResult.ListPage.from(brandId != null
                ? getProductsWithActiveBrandByBrandId(brandId, PageRequest.of(page, size, sortOrder))
                : getProductsWithActiveBrand(PageRequest.of(page, size, sortOrder)));
    }

    @Cacheable(cacheNames = CacheType.Names.PRODUCT_LIST_LIKES,
            key = "(#brandId ?: 'all') + ':' + #page + ':' + #size")
    @Transactional(readOnly = true)
    public ProductResult.ListPage getProductListByLikes(Long brandId, int page, int size) {
        return ProductResult.ListPage.from(brandId != null
                ? getProductsWithActiveBrandByBrandIdSortedByLikes(brandId, page, size)
                : getProductsWithActiveBrandSortedByLikes(page, size));
    }
}
