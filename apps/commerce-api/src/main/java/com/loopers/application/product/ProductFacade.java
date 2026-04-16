package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.domain.product.event.ProductViewedEvent;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.enums.ProductSortType;
import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
 *
 * <h3>캐시 위계 분리</h3>
 * <p>상품 목록 캐시(productList)는 상품 ID 목록 + 페이징 메타만 저장하고,
 * 개별 상품 정보는 productDetail 캐시(L1+L2)에서 조회한다.
 * 이로써 상품 수정 시 목록 캐시를 invalidate할 필요 없이 productDetail만 evict하면 된다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductFacade {

    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;
    private final RankingService rankingService;
    private final ApplicationEventPublisher eventPublisher;

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
    public PagedResult<ProductInfo> getProductsForCustomer(String keyword, Long brandId,
                                                            ProductSortType sort, int page, int size) {
        if (keyword == null && page == 0 && size == 20) {
            return getProductsFromCachedIds(brandId, sort, page, size);
        }
        return getProductsFromDb(keyword, brandId, sort, page, size);
    }

    /**
     * 캐시 위계 분리: productList 캐시에는 ID 목록 + 페이징 메타만 저장한다.
     * 개별 상품 정보는 productDetail 캐시(L1+L2)에서 조회하여 조합한다.
     */
    private PagedResult<ProductInfo> getProductsFromCachedIds(Long brandId,
                                                               ProductSortType sort, int page, int size) {
        ProductListIdCache idCache = getCachedProductListIds(brandId, sort, page, size);

        List<ProductModel> products = idCache.productIds().stream()
                .map(productService::findById)
                .toList();

        List<ProductInfo> enriched = enrichProducts(products);

        return new PagedResult<>(enriched, idCache.page(), idCache.size(),
                idCache.totalElements(), idCache.totalPages());
    }

    @Cacheable(cacheNames = "productList",
               key = "T(String).valueOf(#brandId) + ':' + #sort.name() + ':p' + #page + ':s' + #size")
    public ProductListIdCache getCachedProductListIds(Long brandId,
                                                      ProductSortType sort, int page, int size) {
        PageQuery query = buildPageQuery(sort, page, size);
        PagedResult<ProductModel> productPage = productService.findAllForCustomer(null, brandId, query);

        List<Long> productIds = productPage.content().stream()
                .map(ProductModel::getProductId)
                .toList();

        return new ProductListIdCache(productIds, productPage.page(), productPage.size(),
                productPage.totalElements(), productPage.totalPages());
    }

    private PagedResult<ProductInfo> getProductsFromDb(String keyword, Long brandId,
                                                         ProductSortType sort, int page, int size) {
        PageQuery query = buildPageQuery(sort, page, size);
        PagedResult<ProductModel> productPage = productService.findAllForCustomer(keyword, brandId, query);
        List<ProductInfo> enriched = enrichProducts(productPage.content());

        return new PagedResult<>(enriched, productPage.page(), productPage.size(),
                productPage.totalElements(), productPage.totalPages());
    }

    private PageQuery buildPageQuery(ProductSortType sort, int page, int size) {
        return switch (sort) {
            case LATEST -> new PageQuery(page, size, "createdAt", false);
            case PRICE_ASC -> new PageQuery(page, size, "price", true);
            case LIKES_DESC -> new PageQuery(page, size, "likeCount", false);
        };
    }

    /**
     * 고객용 상품 상세 정보를 조회한다.
     *
     * <p>상품 정보, 재고, 브랜드명, 좋아요 수를 결합하여 반환한다.</p>
     *
     * @param productId 조회할 상품 ID
     * @return 상품 상세 정보 (재고, 브랜드명, 좋아요 수 포함)
     */
    public ProductInfo getProductDetailForCustomer(Long productId) {
        ProductModel product = productService.findById(productId);
        ProductStockModel stock = stockService.findByProductId(productId);
        BrandModel brand = brandService.findById(product.getBrandId());

        Long rank;
        try {
            rank = rankingService.getRank(LocalDate.now(), productId);
        } catch (Exception e) {
            log.warn("[ProductFacade] 순위 조회 실패, rank=null 처리. productId={}", productId, e);
            rank = null;
        }

        eventPublisher.publishEvent(new ProductViewedEvent(productId, null));

        return ProductInfo.from(product, stock, brand.getBrandName(),
                product.getLikeCount(), rank);
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
        List<Long> productIds = products.stream().map(ProductModel::getProductId).toList();
        Map<Long, ProductStockModel> stockMap = stockService.findAllByProductIds(productIds).stream()
                .collect(Collectors.toMap(ProductStockModel::getProductId, Function.identity()));
        return products.stream()
                .map(product -> ProductInfo.from(product, stockMap.get(product.getProductId())))
                .toList();
    }

    /**
     * 상품을 신규 등록한다.
     *
     * <p>브랜드 존재 여부를 검증한 뒤, 상품을 생성하고, 초기 재고를 설정한다.
     * 상품 생성은 목록 구조를 변경하므로 productList 캐시를 전체 무효화한다.</p>
     *
     * @param command 상품 생성 커맨드
     * @return 생성된 상품 정보
     */
    @CacheEvict(cacheNames = "productList", allEntries = true)
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
     * <p>상품을 수정한 뒤 재고 정보를 결합하여 반환한다.
     * 캐시 위계 분리에 의해 productList는 ID 목록만 캐싱하므로,
     * 상품 정보 수정 시 productList evict 불필요 (productDetail만 evict됨).</p>
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
     * 상품 목록에 재고, 브랜드명을 배치 조회하여 결합한다 (N+1 방지).
     * likeCount는 ProductModel에서 직접 읽는다 (비정규화).
     */
    private List<ProductInfo> enrichProducts(List<ProductModel> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(ProductModel::getProductId).toList();
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();

        Map<Long, ProductStockModel> stockMap = stockService.findAllByProductIds(productIds).stream()
                .collect(Collectors.toMap(ProductStockModel::getProductId, Function.identity()));
        Map<Long, BrandModel> brandMap = brandService.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getBrandId, Function.identity()));

        return products.stream()
                .map(product -> {
                    ProductStockModel stock = stockMap.get(product.getProductId());
                    BrandModel brand = brandMap.get(product.getBrandId());
                    String brandName = brand != null ? brand.getBrandName() : null;
                    return ProductInfo.from(product, stock, brandName, product.getLikeCount());
                })
                .toList();
    }
}
