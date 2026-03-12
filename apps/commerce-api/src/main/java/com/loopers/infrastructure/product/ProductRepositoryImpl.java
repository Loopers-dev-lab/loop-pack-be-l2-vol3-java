package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductWithBrand;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Product> findAllByIdsWithLock(List<Long> ids) {
        return productJpaRepository.findAllByIdsWithLock(ids);
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<ProductWithBrand> findAllWithBrand() {
        return productJpaRepository.findAllWithBrand().stream()
            .map(this::toProductWithBrand)
            .toList();
    }

    @Override
    public List<ProductWithBrand> findAllWithBrand(String sort) {
        return productJpaRepository.findAllWithBrand(toSort(sort)).stream()
            .map(this::toProductWithBrand)
            .toList();
    }

    @Override
    public List<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId) {
        return productJpaRepository.findAllByBrandIdWithBrand(brandId).stream()
            .map(this::toProductWithBrand)
            .toList();
    }

    @Override
    public Page<ProductWithBrand> findAllWithBrand(String sort, Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(sort));
        return productJpaRepository.findAllWithBrandPaged(sortedPageable)
            .map(this::toProductWithBrand);
    }

    @Override
    public Page<ProductWithBrand> findAllByBrandIdWithBrand(Long brandId, String sort, Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(sort));
        return productJpaRepository.findAllByBrandIdWithBrandPaged(brandId, sortedPageable)
            .map(this::toProductWithBrand);
    }

    @Override
    public int incrementLikeCount(Long productId) {
        return productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public int decrementLikeCount(Long productId) {
        return productJpaRepository.decrementLikeCount(productId);
    }

    private ProductWithBrand toProductWithBrand(Object[] row) {
        Product product = (Product) row[0];
        String brandName = (String) row[1];
        return new ProductWithBrand(product, brandName, product.getLikeCount());
    }

    private Sort toSort(String sort) {
        if (sort == null) {
            return Sort.by("createdAt").descending();
        }
        return switch (sort) {
            case "price_asc" -> Sort.by("price.value").ascending();
            case "likes_desc" -> Sort.by(
                Sort.Order.desc("likeCount"),
                Sort.Order.desc("id")
            );
            default -> Sort.by("createdAt").descending();
        };
    }
}
