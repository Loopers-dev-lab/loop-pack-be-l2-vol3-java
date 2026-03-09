package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.enums.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 상품 Facade (퍼사드)
 *
 * <p>ProductService, StockService, BrandService, LikeService를 조합(orchestration)하여
 * 상품 비즈니스 플로우를 완성한다.</p>
 *
 * <ul>
 *   <li>트랜잭션 경계 설정</li>
 *   <li>상품 + 재고 정보 결합 조회</li>
 *   <li>브랜드 검증 + 상품 생성 + 재고 생성 오케스트레이션</li>
 *   <li>상품 수정 + 재고 결합</li>
 *   <li>관리자용 상품 목록 조회 (재고 포함)</li>
 *   <li>변경 이력(Revision) 조회</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductFacade {

    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;
    private final LikeService likeService;

    /**
     * 고객용 상품 목록을 정렬 + 페이징하여 조회한다.
     *
     * <p>LATEST/PRICE_ASC는 DB ORDER BY + Pageable로 처리하고,
     * LIKES_DESC는 전체 조회 후 좋아요 수 기준 in-memory 정렬 + 수동 페이징을 수행한다.</p>
     *
     * @param keyword 검색 키워드 (nullable)
     * @param brandId 브랜드 ID 필터 (nullable)
     * @param sort    정렬 기준
     * @param page    페이지 번호 (0부터)
     * @param size    페이지 크기
     * @return 페이징된 상품 정보 목록 (재고, 브랜드명, 좋아요 수 포함)
     */
    public PageResponse<ProductInfo> getProductsForCustomer(String keyword, String brandId,
                                                            ProductSortType sort, int page, int size) {
        if (sort == ProductSortType.LIKES_DESC) {
            return getProductsSortedByLikes(keyword, brandId, page, size);
        }

        Sort dbSort = switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        Page<ProductModel> productPage = productService.findAllForCustomer(keyword, brandId,
                PageRequest.of(page, size, dbSort));
        List<ProductInfo> enriched = enrichProducts(productPage.getContent());

        return new PageResponse<>(enriched, productPage.getNumber(), productPage.getSize(),
                productPage.getTotalElements(), productPage.getTotalPages());
    }

    /**
     * 고객용 상품 상세 정보를 조회한다.
     *
     * <p>상품 정보, 재고, 브랜드명, 좋아요 수를 결합하여 반환한다.</p>
     *
     * @param productId 조회할 상품 ID
     * @return 상품 상세 정보 (재고, 브랜드명, 좋아요 수 포함)
     */
    public ProductInfo getProductDetailForCustomer(String productId) {
        ProductModel product = productService.findById(productId);
        ProductStockModel stock = stockService.findByProductId(productId);
        BrandModel brand = brandService.findById(product.getBrandId());
        long likeCount = likeService.countByProductId(productId);
        return ProductInfo.from(product, stock, brand.getBrandName(), likeCount);
    }

    /**
     * 관리자용 상품 목록을 조회한다.
     *
     * <p>상품 정보와 재고 정보를 결합하여 반환한다.</p>
     *
     * @param includeDeleted 삭제된 상품 포함 여부
     * @return 상품 정보 목록 (재고 포함)
     */
    public List<ProductInfo> getProductsForAdmin(boolean includeDeleted) {
        List<ProductModel> products = productService.findAllForAdmin(includeDeleted);
        return products.stream()
                .map(product -> {
                    ProductStockModel stock = stockService.findByProductId(product.getProductId());
                    return ProductInfo.from(product, stock);
                })
                .toList();
    }

    /**
     * 상품을 신규 등록한다.
     *
     * <p>브랜드 존재 여부를 검증한 뒤, 상품을 생성하고, 초기 재고를 설정한다.</p>
     *
     * @param command 상품 생성 커맨드
     * @return 생성된 상품 정보
     */
    @Transactional
    public ProductInfo createProduct(ProductCreateCommand command) {
        brandService.findById(command.brandId());
        ProductModel product = productService.createProduct(
                command.productName(), command.brandId(), command.price(), command.description());
        ProductStockModel stock = stockService.createStock(product.getProductId(), command.initialStock());
        return ProductInfo.from(product, stock);
    }

    /**
     * 상품 정보를 수정한다.
     *
     * <p>상품을 수정한 뒤 재고 정보를 결합하여 반환한다.</p>
     *
     * @param command 상품 수정 커맨드
     * @return 수정된 상품 정보
     */
    @Transactional
    public ProductInfo updateProduct(ProductUpdateCommand command) {
        ProductModel product = productService.updateProduct(
                command.productId(), command.productName(), command.price(),
                command.description(), command.imageUrl());
        ProductStockModel stock = stockService.findByProductId(command.productId());
        return ProductInfo.from(product, stock);
    }

    /**
     * 좋아요 수 기준 내림차순 정렬 + 수동 페이징. like count가 별도 테이블이므로 DB-level 정렬 불가.
     */
    private PageResponse<ProductInfo> getProductsSortedByLikes(String keyword, String brandId,
                                                               int page, int size) {
        List<ProductModel> allProducts = productService.findAllForCustomer(keyword, brandId);
        List<ProductInfo> enriched = enrichProducts(allProducts);

        List<ProductInfo> sorted = enriched.stream()
                .sorted(Comparator.comparingLong(ProductInfo::getLikeCount).reversed())
                .toList();

        int totalElements = sorted.size();
        int totalPages = (totalElements + size - 1) / size;
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<ProductInfo> pageContent = sorted.subList(fromIndex, toIndex);

        return new PageResponse<>(pageContent, page, size, totalElements, totalPages);
    }

    /**
     * 상품 목록에 재고, 브랜드명, 좋아요 수를 배치 조회하여 결합한다 (N+1 방지).
     */
    private List<ProductInfo> enrichProducts(List<ProductModel> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<String> productIds = products.stream().map(ProductModel::getProductId).toList();
        List<String> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();

        Map<String, BrandModel> brandMap = brandService.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getBrandId, Function.identity()));
        Map<String, Long> likeCountMap = likeService.countByProductIds(productIds);

        return products.stream()
                .map(product -> {
                    ProductStockModel stock = stockService.findByProductId(product.getProductId());
                    BrandModel brand = brandMap.get(product.getBrandId());
                    String brandName = brand != null ? brand.getBrandName() : null;
                    long likeCount = likeCountMap.getOrDefault(product.getProductId(), 0L);
                    return ProductInfo.from(product, stock, brandName, likeCount);
                })
                .toList();
    }
}
