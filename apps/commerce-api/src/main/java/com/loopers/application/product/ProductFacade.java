package com.loopers.application.product;

import com.loopers.application.observability.ProductViewOutboxAsyncPublisher;
import com.loopers.application.product.event.ProductDeletedEvent;
import com.loopers.application.product.event.ProductUpdatedEvent;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortOrder;
import com.loopers.domain.ranking.RankingQueryService;
import com.loopers.domain.ranking.RankingRequestDate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

/**
 * 상품 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → Application Info({@link ProductInfo}, {@link ProductDetailInfo},
 * {@link ProductListItemInfo}) 변환.
 * Controller는 Facade만 호출하며, request는 도메인 파라미터로 변환 후 Service에 전달한다.
 *
 * <p>
 * 좋아요 수 집계: Like 도메인 경계를 지키기 위해 {@link LikeService}만 사용한다.
 * (Repository 직접 주입·호출 금지 → Service를 통한 캡슐화)
 */
@Service
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductCacheService productCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductViewOutboxAsyncPublisher productViewOutboxAsyncPublisher;
    private final RankingQueryService rankingQueryService;

    public ProductFacade(ProductService productService, BrandService brandService, LikeService likeService,
            ProductCacheService productCacheService,
            ApplicationEventPublisher eventPublisher,
            ProductViewOutboxAsyncPublisher productViewOutboxAsyncPublisher,
            RankingQueryService rankingQueryService) {
        this.productService = productService;
        this.brandService = brandService;
        this.likeService = likeService;
        this.productCacheService = productCacheService;
        this.eventPublisher = eventPublisher;
        this.productViewOutboxAsyncPublisher = productViewOutboxAsyncPublisher;
        this.rankingQueryService = rankingQueryService;
    }

    /**
     * 상품 등록
     * @param brandId 브랜드 ID
     * @param name 상품 이름
     * @param price 상품 가격
     * @param stockQuantity 상품 재고 수
     * @return 상품 정보
     */
    @Transactional
    public ProductInfo registerProduct(Long brandId, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.registerProduct(brandId, name, price, stockQuantity);
        return ProductInfo.from(product);
    }

    /**
     * 상품 조회
     * @param id 상품 ID
     * @return 상품 정보
     */
    @Transactional(readOnly = true)
    public Optional<ProductInfo> findById(Long id) {
        return productService.findById(id).map(ProductInfo::from);
    }

    /**
     * 미삭제 상품 조회
     * @param id 상품 ID
     * @return 상품 정보
     */
    @Transactional(readOnly = true)
    public Optional<ProductInfo> findByIdAndNotDeleted(Long id) {
        return productService.findByIdAndNotDeleted(id).map(ProductInfo::from);
    }

    /**
     * 상품 상세 조회
     * <p>
     * 응답의 {@code rankingRank}는 이 요청 시점에 Redis ZSET에서 조회한 값이다. 랭킹 목록 API와 날짜가 같아도
     * 호출 시점이 다르면 ZSET이 갱신되어 목록에 표시된 순위와 숫자가 어긋날 수 있다(오류가 아님).
     *
     * @param productId 상품 ID
     * @param dateYyyyMmDdOptional 랭킹 기준 일자 yyyyMMdd (생략 시 오늘, Asia/Seoul)
     * @return 상품 상세 정보
     */
    @Transactional(readOnly = true)
    public Optional<ProductDetailInfo> getProductDetail(Long productId, String dateYyyyMmDdOptional) {
        // 랭킹 기준 일자를 해석한다.
        LocalDate rankingDate = RankingRequestDate.resolveOptionalYyyyMmDd(dateYyyyMmDdOptional);
        // 캐시에서 상품 상세 정보를 조회한다.
        Optional<ProductDetailInfo> cached = productCacheService.getDetail(productId);
        if (cached.isPresent()) {
            productViewOutboxAsyncPublisher.scheduleRecordProductViewed(productId);
            return Optional.of(withDailyRankingRank(cached.get(), rankingDate, productId));
        }
        Optional<ProductModel> productOpt = productService.findByIdAndNotDeleted(productId);
        if (productOpt.isEmpty()) {
            return Optional.empty();
        }
        ProductModel product = productOpt.get();
        Optional<BrandModel> brandOpt = brandService.findByIdAndNotDeleted(product.getBrandId());
        if (brandOpt.isEmpty()) {
            return Optional.empty();
        }
        long likeCount = likeService.getLikeCountFromStats(productId);
        ProductDetailInfo forCache = new ProductDetailInfo(
                product.getId(),
                product.getBrandId(),
                brandOpt.get().getName(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                likeCount,
                null);
        productCacheService.putDetail(productId, forCache);
        productViewOutboxAsyncPublisher.scheduleRecordProductViewed(productId);
        return Optional.of(withDailyRankingRank(forCache, rankingDate, productId));
    }

    /**
     * 상세 응답에 일간 랭킹 순위를 붙인다({@code ZREVRANK} 기준).
     * <p>
     * 목록 API와 같은 일자·전역 순위를 쓰지만, 각 API가 서로 다른 HTTP 요청에서 ZSET을 읽으므로
     * 동일 스냅샷을 보장하지 않는다.
     *
     * @param base 기준 상품 상세 정보
     * @param rankingDate 랭킹 기준 일자
     * @param productId 상품 ID
     * @return 랭킹 순위를 포함한 상품 상세 정보
     */
    private ProductDetailInfo withDailyRankingRank(ProductDetailInfo base, LocalDate rankingDate, long productId) {
        // 랭킹 순위를 조회한다.
        OptionalLong rank = rankingQueryService.findOneBasedDailyRank(rankingDate, productId);
        // 랭킹 순위를 박싱한다.
        Long rankBoxed = rank.isPresent() ? Long.valueOf(rank.getAsLong()) : null;
        return new ProductDetailInfo(
                base.id(),
                base.brandId(),
                base.brandName(),
                base.name(),
                base.price(),
                base.stockQuantity(),
                base.likeCount(),
                rankBoxed);
    }

    /**
     * 신상품 목록: 등록 최신순만. 인기 랭킹과 분리된 전용 노출(09-ranking-user-scenarios §4.1).
     *
     * @param page 페이지 (0부터)
     * @param size 페이지 크기
     */
    @Transactional(readOnly = true)
    public Page<ProductListItemInfo> getNewArrivals(int page, int size) {
        return getProductList(null, "latest", page, size);
    }

    /**
     * 상품 목록 조회
     * @param brandId 브랜드 ID
     * @param sortParam 정렬 기준
     * @param page 페이지 (0부터)
     * @param size 페이지 크기
     * @return 상품 목록 정보
     */
    @Transactional(readOnly = true)
    public Page<ProductListItemInfo> getProductList(Long brandId, String sortParam, int page, int size) {
        if (page == 0) {
            // 캐시에서 상품 목록 정보를 조회한다.
            Optional<Page<ProductListItemInfo>> cached = productCacheService.getList(brandId, sortParam, size);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        ProductSortOrder sortOrder = ProductSortOrder.fromParam(sortParam);
        // 미삭제 상품 목록을 조회한다.
        Page<ProductModel> productPage = productService.findNotDeletedForList(sortOrder, brandId, page, size);
        // 상품 목록을 조회한다.
        List<ProductModel> products = productPage.getContent();
        if (products.isEmpty()) {
            return new PageImpl<>(List.of(), productPage.getPageable(), productPage.getTotalElements());
        }
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        // 좋아요 수를 조회한다.
        var likeCountMap = likeService.getLikeCountByProductIdsFromStats(productIds);
        // 브랜드 ID 목록을 조회한다.
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).distinct().toList();
        Map<Long, BrandModel> brandMap = brandService.findByIdAndNotDeletedIn(brandIds);
        List<ProductListItemInfo> items = products.stream()
                .map(p -> {
                    String brandName = Optional.ofNullable(brandMap.get(p.getBrandId()))
                            .map(BrandModel::getName)
                            .orElse("");
                    long likeCount = likeCountMap.getOrDefault(p.getId(), 0L);
                    return new ProductListItemInfo(
                            p.getId(),
                            p.getName(),
                            p.getPrice(),
                            p.getBrandId(),
                            brandName,
                            likeCount);
                })
                .collect(Collectors.toList());
        Page<ProductListItemInfo> result = new PageImpl<>(items, productPage.getPageable(),
                productPage.getTotalElements());
        if (page == 0) {
            productCacheService.putList(brandId, sortParam, size, result);
        }
        return result;
    }

    /**
     * 상품 업데이트
     * @param id 상품 ID
     * @param name 상품 이름
     * @param price 상품 가격
     * @param stockQuantity 상품 재고 수
     * @return 상품 정보
     */
    @Transactional
    public ProductInfo updateProduct(Long id, String name, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.updateProduct(id, name, price, stockQuantity);
        eventPublisher.publishEvent(new ProductUpdatedEvent(id));
        return ProductInfo.from(product);
    }

    /**
     * 상품 삭제
     * @param id 상품 ID
     */
    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
        eventPublisher.publishEvent(new ProductDeletedEvent(id));
    }
}
