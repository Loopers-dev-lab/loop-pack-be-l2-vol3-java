package com.loopers.application.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.view.PublicProductListItemView;
import com.loopers.application.product.view.PublicProductListView;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.query.ProductCursorPage;
import com.loopers.domain.product.query.ProductListCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicProductListQueryApplicationService {

    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;

    @Transactional(readOnly = true)
    public PublicProductListView list(ProductListCriteria criteria) {
        ProductCursorPage products = productApplicationService.listByCursor(criteria);
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(
                products.items().stream().map(Product::brandId).toList()
        );
        List<PublicProductListItemView> items = products.items().stream()
                .map(product -> PublicProductListItemView.from(product, brandNames.get(product.brandId())))
                .toList();
        return new PublicProductListView(
                items,
                null,
                products.size(),
                null,
                null,
                products.hasNext(),
                products.nextCursor()
        );
    }
}
