package com.loopers.interfaces.api.product;

import com.loopers.application.product.CreateProductUseCase;
import com.loopers.application.product.DeleteProductUseCase;
import com.loopers.application.product.ProductQueryUseCase;
import com.loopers.application.product.UpdateProductUseCase;
import com.loopers.interfaces.api.common.PageResponse;
import com.loopers.interfaces.api.product.dto.ProductCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductDetailResponse;
import com.loopers.interfaces.api.product.dto.ProductSummaryResponse;
import com.loopers.interfaces.api.product.dto.ProductUpdateRequest;
import com.loopers.domain.model.common.PageResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminController {

    private final CreateProductUseCase createProductUseCase;
    private final UpdateProductUseCase updateProductUseCase;
    private final DeleteProductUseCase deleteProductUseCase;
    private final ProductQueryUseCase productQueryUseCase;

    public ProductAdminController(CreateProductUseCase createProductUseCase,
                                  UpdateProductUseCase updateProductUseCase,
                                  DeleteProductUseCase deleteProductUseCase,
                                  ProductQueryUseCase productQueryUseCase) {
        this.createProductUseCase = createProductUseCase;
        this.updateProductUseCase = updateProductUseCase;
        this.deleteProductUseCase = deleteProductUseCase;
        this.productQueryUseCase = productQueryUseCase;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductSummaryResponse>> getProducts(
            @RequestParam(required = false) Long brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<ProductQueryUseCase.ProductSummaryInfo> products =
                productQueryUseCase.getProducts(brandId, null, page, size);
        return ResponseEntity.ok(PageResponse.from(products, ProductSummaryResponse::from));
    }

    @PostMapping
    public ResponseEntity<Void> createProduct(@RequestBody ProductCreateRequest request) {
        createProductUseCase.createProduct(request.toCommand());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> updateProduct(@PathVariable Long productId,
                                              @RequestBody ProductUpdateRequest request) {
        updateProductUseCase.updateProduct(request.toCommand(productId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        deleteProductUseCase.deleteProduct(productId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable Long productId) {
        ProductQueryUseCase.ProductDetailInfo info = productQueryUseCase.getProduct(productId);
        return ResponseEntity.ok(ProductDetailResponse.from(info));
    }
}
