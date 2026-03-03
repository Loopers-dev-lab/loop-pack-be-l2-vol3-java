package com.loopers.application.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.view.ProductListView;
import com.loopers.application.product.view.ProductView;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.query.ProductListCriteria;
import com.loopers.domain.product.query.ProductListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductQueryFacade {

    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;
    private final ProductService productService;

    public ProductView get(UUID productId) {
        Product product = productApplicationService.get(productId);
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(List.of(product.brandId()));
        return ProductView.from(product, brandNames.get(product.brandId()));
    }

    public ProductView getIncludingDeleted(UUID productId) {
        Product product = productApplicationService.getIncludingDeleted(productId);
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(List.of(product.brandId()));
        return ProductView.from(product, brandNames.get(product.brandId()));
    }

    public ProductListView list(ProductListQuery query) {
        ProductListCriteria criteria = productService.toCriteria(query);
        Page<Product> products = productApplicationService.list(criteria);
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(
                products.getContent().stream().map(Product::brandId).toList()
        );
        List<ProductView> items = products.getContent().stream()
                .map(product -> ProductView.from(product, brandNames.get(product.brandId())))
                .toList();
        return new ProductListView(
                items,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages()
        );
    }

    public ProductListView listIncludingDeleted(ProductListQuery query) {
        ProductListCriteria criteria = productService.toCriteria(query);
        Page<Product> products = productApplicationService.listIncludingDeleted(criteria);
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(
                products.getContent().stream().map(Product::brandId).toList()
        );
        List<ProductView> items = products.getContent().stream()
                .map(product -> ProductView.from(product, brandNames.get(product.brandId())))
                .toList();
        return new ProductListView(
                items,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages()
        );
    }

    public ProductView toView(Product product) {
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(List.of(product.brandId()));
        return ProductView.from(product, brandNames.get(product.brandId()));
    }

    public ProductListView toListView(Page<Product> products) {
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(
                products.getContent().stream().map(Product::brandId).toList()
        );
        List<ProductView> items = products.getContent().stream()
                .map(product -> ProductView.from(product, brandNames.get(product.brandId())))
                .toList();
        return new ProductListView(
                items,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages()
        );
    }
}
