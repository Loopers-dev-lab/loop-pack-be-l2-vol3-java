package com.loopers.application.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.RegisterProductCommand;
import com.loopers.domain.product.UpdateProductCommand;
import com.loopers.domain.productlike.ProductLikeService;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final ProductLikeService productLikeService;
    private final BrandService brandService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;
    private final RankingRepository rankingRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @CacheEvict(value = {"products", "products:brand"}, allEntries = true)
    @Transactional
    public ProductInfo registerProduct(RegisterProductCommand command) {
        Brand brand = brandService.getBrand(command.brandId());
        Product product = productService.register(command);
        return ProductInfo.from(product, brand);
    }

    @Transactional
    public ProductInfo getProduct(Long id) {
        Product product = productService.getById(id);
        Brand brand = brandService.getBrand(product.getBrandId());
        saveViewEvent(id);

        String today = LocalDate.now().format(DATE_FORMAT);
        Long rawRank = rankingRepository.findRank(today, id);
        Long rank = rawRank != null ? rawRank + 1 : null;

        return ProductInfo.from(product, brand, rank);
    }

    private void saveViewEvent(Long productId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("productId", productId));
            OutboxEvent outboxEvent = OutboxEvent.create(
                    "catalog-events", "PRODUCT_VIEWED", String.valueOf(productId), payload
            );
            outboxEventService.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
        }
    }

    @Cacheable(value = "products", key = "#pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort.toString()")
    public Page<ProductInfo> getProducts(Pageable pageable) {
        Page<Product> products = productService.getAll(pageable);
        Map<Long, Brand> brandMap = getBrandMap(products.getContent());
        return products.map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId())));
    }

    @Cacheable(value = "products:brand", key = "#brandId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort.toString()")
    public Page<ProductInfo> getProductsByBrandId(Long brandId, Pageable pageable) {
        Brand brand = brandService.getBrand(brandId);
        return productService.getAllByBrandId(brandId, pageable)
                .map(p -> ProductInfo.from(p, brand));
    }

    public List<ProductInfo> getProductsByIds(List<Long> ids) {
        List<Product> products = productService.getByIds(ids);
        Map<Long, Brand> brandMap = getBrandMap(products);
        return products.stream()
                .map(p -> ProductInfo.from(p, brandMap.get(p.getBrandId())))
                .toList();
    }

    @CacheEvict(value = {"products", "products:brand"}, allEntries = true)
    @Transactional
    public ProductInfo updateProduct(Long id, UpdateProductCommand command) {
        Product product = productService.update(id, command);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand);
    }

    private Map<Long, Brand> getBrandMap(List<Product> products) {
        List<Long> brandIds = products.stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();
        return brandService.getByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, b -> b));
    }

    @CacheEvict(value = {"products", "products:brand"}, allEntries = true)
    @Transactional
    public void deleteProduct(Long id) {
        // 상품 삭제 전에 좋아요 먼저 삭제
        productLikeService.deleteByProductId(id);
        
        // 상품 삭제 (Soft Delete)
        productService.delete(id);
    }
}
